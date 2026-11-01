package com.freemusic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Internet Archive (archive.org) — a free, open, keyless catalogue of public
 * domain and Creative Commons audio: live concert recordings, 78rpm records,
 * netlabels, old radio, classical. No account, no API key.
 *
 * Listing is cheap; the actual MP3 URL only comes from the per-item metadata
 * endpoint, so it is resolved lazily when the user taps a card.
 */
object InternetArchive {

    private val client = OkHttpClient()

    /**
     * Real music only: the netlabel / music / live-concert / 78rpm collections, with the
     * big spoken-word collections explicitly excluded. Without those exclusions
     * `opensource_audio` floods the grid with speeches, sermons, language lessons,
     * audiobooks and personal recordings.
     */
    private const val MUSIC_ONLY =
        "mediatype:(audio) AND format:(MP3) AND (" +
            "collection:(netlabels) OR collection:(audio_music) OR collection:(etree) OR " +
            "collection:(georgeblood) OR collection:(78rpm) OR collection:(opensource_audio)" +
            ") AND NOT collection:(librivox) AND NOT collection:(audio_bookspoetry) AND " +
            "NOT collection:(podcasts) AND NOT collection:(radioprograms) AND " +
            "NOT collection:(oldtimeradio) AND NOT collection:(audio_news) AND " +
            "NOT collection:(audio_foreign)"

    /** @param collection optional archive.org collection to narrow the search further */
    suspend fun fetch(query: String?, collection: String? = null): List<Track> =
        withContext(Dispatchers.IO) {
        val q = when {
            !collection.isNullOrBlank() && query.isNullOrBlank() ->
                "$MUSIC_ONLY AND collection:($collection)"
            !collection.isNullOrBlank() -> "$MUSIC_ONLY AND collection:($collection) AND ($query)"
            query.isNullOrBlank() -> MUSIC_ONLY
            else -> "$MUSIC_ONLY AND ($query)"
        }

        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "creator")
            .addQueryParameter("rows", "100")
            .addQueryParameter("page", "1")
            // most-downloaded first: keeps the grid full of real, popular music
            .addQueryParameter("sort[]", "downloads desc")
            .addQueryParameter("output", "json")
            .build()

        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw IOException("Khaali jawab mila")
            if (!response.isSuccessful) throw IOException("Archive error ${response.code}")

            val docs = JSONObject(text)
                .optJSONObject("response")
                ?.optJSONArray("docs")
                ?: return@withContext emptyList()

            val out = ArrayList<Track>(docs.length())
            for (i in 0 until docs.length()) {
                val o = docs.optJSONObject(i) ?: continue
                val identifier = o.optString("identifier")
                if (identifier.isBlank()) continue

                out.add(
                    Track(
                        id = identifier,
                        title = firstOf(o, "title").ifBlank { identifier },
                        artist = firstOf(o, "creator").ifBlank { "Internet Archive" },
                        album = "",
                        audioUrl = "", // resolved on tap
                        imageUrl = "https://archive.org/services/img/$identifier",
                        durationMs = 0L,
                        licenseUrl = "",
                        shareUrl = "https://archive.org/details/$identifier"
                    )
                )
            }
            out
        }
    }

    /** Finds the first playable MP3 inside an item. */
    suspend fun resolveAudioUrl(identifier: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://archive.org/metadata/$identifier").build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val text = response.body?.string().orEmpty()
            val files = JSONObject(text).optJSONArray("files") ?: return@withContext null

            var best: String? = null
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name")
                val format = f.optString("format").lowercase()
                if (name.endsWith(".mp3", ignoreCase = true) &&
                    (format.contains("mp3") || format.contains("vbr"))
                ) {
                    best = name
                    break
                }
            }
            best?.let {
                "https://archive.org/download/$identifier/" + URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
        }
    }

    /** Archive returns some fields as a plain string and others as an array. */
    private fun firstOf(o: JSONObject, key: String): String {
        val value = o.opt(key) ?: return ""
        return when (value) {
            is String -> value
            is JSONArray -> (0 until value.length())
                .mapNotNull { value.optString(it).takeIf { s -> s.isNotBlank() } }
                .firstOrNull()
                .orEmpty()
            else -> value.toString()
        }
    }
}
