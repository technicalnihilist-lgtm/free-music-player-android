package com.freemusic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Spotify Web API calls. Audio does not come from here — Spotify never serves
 * raw audio to third-party apps. Instead we ask the Spotify app already
 * running on the user's account to play a track, which is the only supported
 * way to stream the licensed catalogue (and needs Premium).
 */
object SpotifyApi {

    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun search(token: String, query: String): List<Track> = withContext(Dispatchers.IO) {
        val url = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "50")
            .addQueryParameter("market", "IN")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Spotify search fail: ${response.code}")
            val items = JSONObject(text)
                .optJSONObject("tracks")
                ?.optJSONArray("items")
                ?: return@withContext emptyList()

            val out = ArrayList<Track>(items.length())
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue

                val artist = o.optJSONArray("artists")
                    ?.optJSONObject(0)
                    ?.optString("name")
                    .orEmpty()

                val album = o.optJSONObject("album")
                val images = album?.optJSONArray("images")
                val image = images?.optJSONObject(0)?.optString("url").orEmpty()

                out.add(
                    Track(
                        id = id,
                        title = o.optString("name", "Untitled"),
                        artist = artist.ifBlank { "Spotify" },
                        album = album?.optString("name").orEmpty(),
                        audioUrl = "",
                        imageUrl = image,
                        durationMs = o.optLong("duration_ms"),
                        licenseUrl = "",
                        shareUrl = o.optString("uri")
                    )
                )
            }
            out
        }
    }

    /** Plays a track on whichever Spotify device the account is currently active on. */
    suspend fun playTrack(token: String, trackUri: String): String = withContext(Dispatchers.IO) {
        val deviceId = activeDeviceId(token)
            ?: throw IOException(
                "Koi active Spotify device nahi mila. Phone pe Spotify app kholein, " +
                    "ek gaana bajayein, phir yahan se try karein."
            )

        val body = JSONObject().put("uris", org.json.JSONArray().put(trackUri))
            .toString()
            .toRequestBody(jsonType)

        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player/play")
            .header("Authorization", "Bearer $token")
            .put(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val reason = when (response.code) {
                    403 -> "Spotify Premium chahiye playback ke liye."
                    404 -> "Active device nahi mila. Spotify app kholein aur ek gaana bajayein."
                    else -> "Spotify error ${response.code}"
                }
                throw IOException(reason)
            }
            "Spotify app par chal raha hai"
        }
    }

    private fun activeDeviceId(token: String): String? {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player/devices")
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string().orEmpty()
            val devices = JSONObject(text).optJSONArray("devices") ?: return null
            for (i in 0 until devices.length()) {
                val d = devices.optJSONObject(i) ?: continue
                if (d.optBoolean("is_active", false)) return d.optString("id")
            }
            if (devices.length() > 0) return devices.optJSONObject(0)?.optString("id")
            return null
        }
    }
}
