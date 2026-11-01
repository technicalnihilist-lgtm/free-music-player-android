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

    /** @param collection optional archive.org collection, e.g. "musopen" for public-domain classical */
    suspend fun fetch(query: String?, collection: String? = null): List<Track> =
        withContext(Dispatchers.IO) {
        val q = when {
            !collection.isNullOrBlank() && query.isNullOrBlank() ->
                "mediatype:(audio) AND format:(MP3) AND collection:($collection)"
            !collection.isNullOrBlank() ->
                "mediatype:(audio) AND format:(MP3) AND collection:($collection) AND ($query)"
            query.isNullOrBlank() ->
                "mediatype:(audio) AND (collection:(opensource_audio) OR " +
                    "licenseurl:(*creativecommons*)) AND format:(MP3)"
            else -> "mediatype:(audio) AND format:(MP3) AND ($query)"
        }

        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "creator")
            .addQueryParameter("rows", "60")
            .addQueryParameter("page", "1")
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
