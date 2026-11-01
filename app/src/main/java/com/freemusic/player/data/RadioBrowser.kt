package com.freemusic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

/**
 * Radio Browser — a free, community-run directory of thousands of internet radio
 * stations. No key, no account. Each station's licensing is handled by the
 * station itself, so streaming it is just like tuning a radio.
 *
 * Stations are live streams, so there is no duration; the player treats them as
 * endless tracks.
 */
object RadioBrowser {

    private val client = OkHttpClient()

    // Community mirrors; any one of them can be down at a given time.
    private val bases = listOf(
        "https://de1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://de2.api.radio-browser.info"
    )

    suspend fun fetch(query: String?): List<Track> = withContext(Dispatchers.IO) {
        var lastError: IOException? = null
        for (base in bases) {
            try {
                return@withContext request(base, query)
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Radio Browser tak nahi pahunch paye")
    }

    private fun request(base: String, query: String?): List<Track> {
        val urlBuilder = "$base/json/stations/search".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "60")
            .addQueryParameter("hidebroken", "true")
            .addQueryParameter("order", "clickcount")
            .addQueryParameter("reverse", "true")

        if (!query.isNullOrBlank()) {
            urlBuilder.addQueryParameter("name", query.trim())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "FreeMusicPlayer/1.5")
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw IOException("Khaali jawab mila")
            if (!response.isSuccessful) throw IOException("Radio error ${response.code}")

            val arr = JSONArray(text)
            val out = ArrayList<Track>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
                if (stream.isBlank() || !stream.startsWith("http")) continue

                val name = o.optString("name").ifBlank { "Radio" }
                val country = o.optString("country").ifBlank { "Internet radio" }
                val tags = o.optString("tags").split(",").firstOrNull()?.trim().orEmpty()

                out.add(
                    Track(
                        id = o.optString("stationuuid").ifBlank { name },
                        title = name,
                        artist = if (tags.isNotBlank()) "$country · $tags" else country,
                        album = "",
                        audioUrl = stream,
                        imageUrl = o.optString("favicon"),
                        durationMs = 0L,
                        licenseUrl = "",
                        shareUrl = o.optString("homepage")
                    )
                )
            }
            out
        }
    }
}
