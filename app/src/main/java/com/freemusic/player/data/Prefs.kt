package com.freemusic.player.data

import android.content.Context

/**
 * Tiny SharedPreferences wrapper. The Jamendo client id is entered once on
 * first launch and remembered after that, so no code editing is needed.
 */
object Prefs {

    private const val FILE = "freemusic_prefs"
    private const val KEY_CLIENT_ID = "jamendo_client_id"

    fun clientId(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_CLIENT_ID, "")
            .orEmpty()

    fun setClientId(context: Context, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIENT_ID, value.trim())
            .apply()
    }

    // ---- Spotify ----

    private const val KEY_SP_CLIENT = "spotify_client_id"
    private const val KEY_SP_ACCESS = "spotify_access_token"
    private const val KEY_SP_REFRESH = "spotify_refresh_token"
    private const val KEY_SP_EXPIRES = "spotify_expires_at"
    private const val KEY_SP_VERIFIER = "spotify_code_verifier"
    private const val KEY_SP_STATE = "spotify_state"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun spotifyClientId(context: Context): String =
        prefs(context).getString(KEY_SP_CLIENT, "").orEmpty()

    fun setSpotifyClientId(context: Context, value: String) {
        prefs(context).edit().putString(KEY_SP_CLIENT, value.trim()).apply()
    }

    fun spotifyAccessToken(context: Context): String =
        prefs(context).getString(KEY_SP_ACCESS, "").orEmpty()

    fun spotifyRefreshToken(context: Context): String =
        prefs(context).getString(KEY_SP_REFRESH, "").orEmpty()

    fun spotifyExpiresAt(context: Context): Long =
        prefs(context).getLong(KEY_SP_EXPIRES, 0L)

    fun setSpotifyTokens(context: Context, accessToken: String, refreshToken: String, expiresAt: Long) {
        prefs(context).edit()
            .putString(KEY_SP_ACCESS, accessToken)
            .putString(KEY_SP_REFRESH, refreshToken)
            .putLong(KEY_SP_EXPIRES, expiresAt)
            .apply()
    }

    fun spotifyVerifier(context: Context): String =
        prefs(context).getString(KEY_SP_VERIFIER, "").orEmpty()

    fun spotifyState(context: Context): String =
        prefs(context).getString(KEY_SP_STATE, "").orEmpty()

    fun setSpotifyPending(context: Context, verifier: String, state: String) {
        prefs(context).edit()
            .putString(KEY_SP_VERIFIER, verifier)
            .putString(KEY_SP_STATE, state)
            .apply()
    }

    fun clearSpotify(context: Context) {
        prefs(context).edit()
            .remove(KEY_SP_ACCESS)
            .remove(KEY_SP_REFRESH)
            .remove(KEY_SP_EXPIRES)
            .remove(KEY_SP_VERIFIER)
            .remove(KEY_SP_STATE)
            .apply()
    }
}
