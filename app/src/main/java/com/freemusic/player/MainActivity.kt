package com.freemusic.player

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.freemusic.player.data.Prefs
import com.freemusic.player.data.SpotifyAuth
import com.freemusic.player.ui.App
import com.freemusic.player.ui.theme.FreeMusicTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleSpotifyRedirect(intent)

        setContent {
            FreeMusicTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSpotifyRedirect(intent)
    }

    /**
     * Catches the freemusic://callback redirect from the Spotify authorize page
     * and exchanges the authorization code for tokens (PKCE, no client secret).
     */
    private fun handleSpotifyRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "freemusic") return

        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        val error = data.getQueryParameter("error")

        if (!error.isNullOrBlank() || code.isNullOrBlank()) {
            SpotifyAuth.sessionVersion.value = SpotifyAuth.sessionVersion.value + 1
            return
        }

        // CSRF guard: the state we stored before opening the browser must match.
        if (state != Prefs.spotifyState(this)) return

        val verifier = Prefs.spotifyVerifier(this)
        val clientId = Prefs.spotifyClientId(this)
        if (verifier.isBlank() || clientId.isBlank()) return

        lifecycleScope.launch {
            runCatching { SpotifyAuth.exchangeCode(this@MainActivity, clientId, code, verifier) }
            SpotifyAuth.sessionVersion.value = SpotifyAuth.sessionVersion.value + 1
        }
    }
}
