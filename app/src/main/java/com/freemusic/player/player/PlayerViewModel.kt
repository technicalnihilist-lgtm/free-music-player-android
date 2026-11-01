package com.freemusic.player.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.freemusic.player.data.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artwork: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasItem: Boolean = false,
    val shuffle: Boolean = false,
    val repeatAll: Boolean = false
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()

        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                sync(c)
                _state.update { it.copy(isConnected = true) }
                startTicker()
            } catch (_: Exception) {
                _state.update { it.copy(isConnected = false) }
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun sync(player: Player) {
        val metadata = player.currentMediaItem?.mediaMetadata
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                hasItem = player.currentMediaItem != null,
                title = metadata?.title?.toString().orEmpty(),
                artist = metadata?.artist?.toString().orEmpty(),
                artwork = metadata?.artworkUri?.toString().orEmpty(),
                durationMs = if (player.duration > 0) player.duration else it.durationMs,
                shuffle = player.shuffleModeEnabled,
                repeatAll = player.repeatMode == Player.REPEAT_MODE_ALL
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) {
                    val pos = runCatching { c.currentPosition }.getOrDefault(0L)
                    _state.update { it.copy(positionMs = pos) }
                }
            }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 4000L) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val c = controller ?: return
        c.repeatMode =
            if (c.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF
            else Player.REPEAT_MODE_ALL
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply { if (imageUrl.isNotBlank()) setArtworkUri(Uri.parse(imageUrl)) }
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(audioUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onCleared() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
