package com.freemusic.player.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Spotify login using the Authorization Code flow with PKCE.
 *
 * Why not the official spotify-auth AAR: version 2.1.0 has no PKCE support
 * (its AuthorizationRequest.Builder cannot send code_challenge), and its
 * default implicit-grant path (AuthorizationResponse.Type.TOKEN) was sunset
 * by Spotify on 27 November 2025. So we run the documented PKCE flow
 * ourselves — no client secret is ever stored or shipped.
 */
object SpotifyAuth {

    const val REDIRECT_URI = "freemusic://callback"

    private const val SCOPES = "user-read-playback-state user-modify-playback-state"

    private val client = OkHttpClient()
    private val random = SecureRandom()

    /** Bumped whenever login state changes, so the UI can re-read the tokens. */
    val sessionVersion = MutableStateFlow(0)

    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun newVerifier(): String {
        val sb = StringBuilder(64)
        repeat(64) { sb.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }

    fun newState(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun authorizeUrl(clientId: String, verifier: String, state: String): String {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        return buildString {
            append("https://accounts.spotify.com/authorize")
            append("?response_type=code")
            append("&client_id=").append(enc(clientId))
            append("&scope=").append(enc(SCOPES))
            append("&redirect_uri=").append(enc(REDIRECT_URI))
            append("&state=").append(enc(state))
            append("&code_challenge_method=S256")
            append("&code_challenge=").append(enc(challenge(verifier)))
        }
    }

    /** Swaps the authorization code for tokens. No client secret needed with PKCE. */
    suspend fun exchangeCode(
        context: Context,
        clientId: String,
        code: String,
        verifier: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Spotify login fail: ${response.code}")
            val json = JSONObject(text)
            val access = json.optString("access_token")
            if (access.isBlank()) throw IOException("Token nahi mila")
            Prefs.setSpotifyTokens(
                context,
                accessToken = access,
                refreshToken = json.optString("refresh_token"),
                expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
            )
            true
        }
    }

    /** Returns a usable access token, refreshing it when it has expired. */
    suspend fun accessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val current = Prefs.spotifyAccessToken(context)
        if (current.isNotBlank() && System.currentTimeMillis() < Prefs.spotifyExpiresAt(context) - 60_000L) {
            return@withContext current
        }

        val clientId = Prefs.spotifyClientId(context)
        val refresh = Prefs.spotifyRefreshToken(context)
        if (clientId.isBlank() || refresh.isBlank()) return@withContext null

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(text)
                val access = json.optString("access_token")
                if (access.isBlank()) return@withContext null
                Prefs.setSpotifyTokens(
                    context,
                    accessToken = access,
                    refreshToken = json.optString("refresh_token").ifBlank { refresh },
                    expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
                )
                access
            }
        }.getOrNull()
    }
}
