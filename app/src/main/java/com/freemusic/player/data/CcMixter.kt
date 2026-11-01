package com.freemusic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * ccMixter — a Creative Commons remix community (since 2004): a cappellas, stems,
 * remixes and full tracks. The ccHost Query API is public, needs no key, and
 * ccMixter explicitly allows third-party apps and devices to use it.
 *
 * The response shape has varied over the years, so parsing is defensive: it
 * accepts a bare JSON array or an object wrapping one, and several possible
 * field names for the audio URL.
 */
object CcMixter {

    private val client = OkHttpClient()

    suspend fun fetch(query: String?): List<Track> = withContext(Dispatchers.IO) {
        val urlBuilder = "https://ccmixter.org/api/query".toHttpUrl().newBuilder()
            .addQueryParameter("f", "json")
            .addQueryParameter("limit", "60")
            .addQueryParameter("sort", "date")
            .addQueryParameter("ord", "desc")

        if (!query.isNullOrBlank()) {
            urlBuilder.addQueryParameter("search", query.trim())
            urlBuilder.addQueryParameter("search_type", "any")
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "FreeMusicPlayer/1.5")
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw IOException("Khaali jawab mila")
            if (!response.isSuccessful) throw IOException("ccMixter error ${response.code}")

            val records = asRecords(text) ?: return@withContext emptyList()

            val out = ArrayList<Track>(records.length())
            for (i in 0 until records.length()) {
                val o = records.optJSONObject(i) ?: continue

                val audio = firstNonBlank(
                    o,
                    "download_url",
                    "download_files",
                    "file_download_url",
                    "files"
                )
                if (audio.isBlank() || !audio.startsWith("http")) continue

                val title = firstNonBlank(o, "upload_name", "name", "title")
                if (title.isBlank()) continue

                val artist = firstNonBlank(o, "user_real_name", "user_name", "artist")
                val pageUrl = firstNonBlank(o, "file_page_url", "permalink", "upload_page_url")
                val image = firstNonBlank(o, "upload_image_url", "image_url", "artwork")

                out.add(
                    Track(
                        id = firstNonBlank(o, "upload_id", "id").ifBlank { title },
                        title = title,
                        artist = artist.ifBlank { "ccMixter" },
                        album = "",
                        audioUrl = audio,
                        imageUrl = image,
                        durationMs = 0L,
                        licenseUrl = firstNonBlank(o, "license_url"),
                        shareUrl = pageUrl
                    )
                )
            }
            out
        }
    }

    /** ccHost can return a bare array, or an object holding the array. */
    private fun asRecords(text: String): JSONArray? {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        if (!trimmed.startsWith("{")) return null
        val obj = JSONObject(trimmed)
        for (key in listOf("records", "results", "data", "uploads")) {
            obj.optJSONArray(key)?.let { return it }
        }
        return null
    }

    private fun firstNonBlank(o: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = o.opt(key) ?: continue
            val s = when (value) {
                is String -> value
                is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { idx ->
                    val item = value.opt(idx)
                    when (item) {
                        is String -> item.takeIf { it.isNotBlank() }
                        is JSONObject -> item.optString("download_url")
                            .ifBlank { item.optString("url") }
                            .takeIf { it.isNotBlank() }
                        else -> null
                    }
                }.orEmpty()
                else -> ""
            }
            if (s.isNotBlank()) return s
        }
        return ""
    }
}
