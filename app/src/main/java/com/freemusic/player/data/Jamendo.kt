package com.freemusic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * One playable track. All audio comes from Jamendo, which serves Creative Commons
 * licensed music with the artist's permission — this is why it is legal to stream.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val audioUrl: String,
    val imageUrl: String,
    val durationMs: Long,
    val licenseUrl: String,
    val shareUrl: String
)

object Jamendo {

    private val client = OkHttpClient()

    /**
     * @param clientId  free Jamendo API client id
     * @param query     free text search, or null for popular tracks
     * @param tag       genre tag such as "rock", or null for no genre filter
     */
    suspend fun fetch(
        clientId: String,
        query: String?,
        tag: String?
    ): List<Track> = withContext(Dispatchers.IO) {

        val urlBuilder = "https://api.jamendo.com/v3.0/tracks/".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "80")
            .addQueryParameter("audioformat", "mp32")
            .addQueryParameter("include", "musicinfo")
            .addQueryParameter("imagesize", "400")

        if (!query.isNullOrBlank()) {
            urlBuilder.addQueryParameter("namesearch", query.trim())
        } else {
            urlBuilder.addQueryParameter("order", "popularity_total")
        }

        if (!tag.isNullOrBlank()) {
            urlBuilder.addQueryParameter("fuzzytags", tag)
        }

        val request = Request.Builder().url(urlBuilder.build()).build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Khaali jawab mila")

            if (!response.isSuccessful) {
                throw IOException("Server error ${response.code}")
            }

            val root = JSONObject(body)
            val headers = root.optJSONObject("headers")
            val status = headers?.optString("status") ?: "success"

            if (status != "success") {
                val msg = headers?.optString("error_message").orEmpty()
                throw IOException(msg.ifBlank { "Jamendo request fail hui" })
            }

            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val list = ArrayList<Track>(results.length())

            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val audio = o.optString("audio")
                if (audio.isBlank()) continue

                val image = o.optString("album_image").ifBlank { o.optString("image") }

                list.add(
                    Track(
                        id = o.optString("id"),
                        title = o.optString("name", "Untitled"),
                        artist = o.optString("artist_name", "Unknown artist"),
                        album = o.optString("album_name", ""),
                        audioUrl = audio,
                        imageUrl = image,
                        durationMs = o.optLong("duration") * 1000L,
                        licenseUrl = o.optString("license_ccurl"),
                        shareUrl = o.optString("shareurl")
                    )
                )
            }
            list
        }
    }
}
