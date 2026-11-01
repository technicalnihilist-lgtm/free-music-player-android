package com.freemusic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Audius — an open music catalog where artists upload their own recordings.
 *
 * The read API needs no key and no account: every request just carries an
 * `app_name` string that we choose ourselves. `GET /tracks/{id}/stream`
 * returns real playable audio for tracks flagged `is_streamable`, which is
 * why a proper player can be built on it.
 *
 * Audius runs as a network of independently operated discovery nodes, so we
 * try a few known hosts in order instead of pinning one.
 */
object Audius {

    private const val APP_NAME = "FreeMusicPlayer"

    private val client = OkHttpClient()

    private val bases = listOf(
        "https://api.audius.co/v1",
        "https://discoveryprovider.audius.co/v1",
        "https://discoveryprovider2.audius.co/v1",
        "https://discoveryprovider3.audius.co/v1"
    )

    suspend fun fetch(query: String?, genre: String?): List<Track> =
        withContext(Dispatchers.IO) {
            var lastError: IOException? = null
            for (base in bases) {
                try {
                    return@withContext request(base, query, genre)
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("Audius server tak nahi pahunch paye")
        }

    private fun request(base: String, query: String?, genre: String?): List<Track> {
        val searching = !query.isNullOrBlank()
        val path = if (searching) "/tracks/search" else "/tracks/trending"

        val urlBuilder = (base + path).toHttpUrl().newBuilder()
            .addQueryParameter("app_name", APP_NAME)
            .addQueryParameter("limit", "60")

        if (searching) {
            urlBuilder.addQueryParameter("query", query!!.trim())
        } else {
            urlBuilder.addQueryParameter("time", "week")
            if (!genre.isNullOrBlank()) urlBuilder.addQueryParameter("genre", genre)
        }

        val request = Request.Builder().url(urlBuilder.build()).build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Khaali jawab mila")
            if (!response.isSuccessful) throw IOException("Audius error ${response.code}")

            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: return emptyList()

            val out = ArrayList<Track>(data.length())
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                if (!o.optBoolean("is_streamable", false)) continue

                val id = o.optString("id")
                if (id.isBlank()) continue

                val user = o.optJSONObject("user")
                val artwork = o.optJSONObject("artwork")
                val image = artwork?.optString("480x480").orEmpty()
                    .ifBlank { artwork?.optString("150x150").orEmpty() }
                val handle = user?.optString("handle").orEmpty()

                out.add(
                    Track(
                        id = id,
                        title = o.optString("title", "Untitled"),
                        artist = user?.optString("name").orEmpty().ifBlank { handle },
                        album = o.optString("genre").orEmpty(),
                        audioUrl = "$base/tracks/$id/stream?app_name=$APP_NAME",
                        imageUrl = image,
                        durationMs = o.optLong("duration") * 1000L,
                        licenseUrl = "",
                        shareUrl = if (handle.isNotBlank()) "https://audius.co/$handle" else "https://audius.co"
                    )
                )
            }
            return out
        }
    }
}
