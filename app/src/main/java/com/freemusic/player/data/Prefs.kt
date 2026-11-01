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
}
