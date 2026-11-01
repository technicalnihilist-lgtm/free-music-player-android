package com.freemusic.player.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.freemusic.player.data.Jamendo
import com.freemusic.player.data.Prefs
import com.freemusic.player.data.Track
import com.freemusic.player.player.PlayerUiState
import com.freemusic.player.player.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val GENRES = listOf(
    "All" to null,
    "Pop" to "pop",
    "Rock" to "rock",
    "Electronic" to "electronic",
    "Hip-Hop" to "hiphop",
    "Jazz" to "jazz",
    "Classical" to "classical",
    "Ambient" to "ambient",
    "Lounge" to "lounge",
    "World" to "world"
)

// ---------------------------------------------------------------- entry point

@Composable
fun App() {
    val context = LocalContext.current
    var clientId by remember { mutableStateOf(Prefs.clientId(context)) }

    if (clientId.isBlank()) {
        SetupScreen(
            onSave = { id ->
                Prefs.setClientId(context, id)
                clientId = id.trim()
            }
        )
    } else {
        MainScreen(
            onChangeKey = {
                Prefs.setClientId(context, "")
                clientId = ""
            }
        )
    }
}

// ---------------------------------------------------------------- first launch

@Composable
private fun SetupScreen(onSave: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Free Music",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Ye app Jamendo se Creative Commons licensed music stream karta hai. " +
                "Chalane ke liye ek free API key chahiye — 2 minute ka kaam hai.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "1. devportal.jamendo.com par free account banayein\n" +
                "2. Naya app banayein, Client ID copy karein\n" +
                "3. Neeche paste karein",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://devportal.jamendo.com/"))
                )
            }
        }) {
            Text("devportal.jamendo.com kholein")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Jamendo Client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { if (text.isNotBlank()) onSave(text) },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Shuru karein")
        }
    }
}

// ---------------------------------------------------------------- browse VM

class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var job: Job? = null

    fun load(query: String?, tag: String?) {
        val clientId = Prefs.clientId(getApplication())
        if (clientId.isBlank()) return

        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val list = Jamendo.fetch(clientId, query, tag)
                _tracks.value = list
                if (list.isEmpty()) _error.value = "Is search ke liye kuch nahi mila."
            } catch (e: Exception) {
                _error.value = e.message ?: "Network problem. Internet check karein."
            } finally {
                _loading.value = false
            }
        }
    }
}

// ---------------------------------------------------------------- main screen

@Composable
private fun MainScreen(onChangeKey: () -> Unit) {

    val browse: BrowseViewModel = viewModel()
    val player: PlayerViewModel = viewModel()

    val tracks by browse.tracks.collectAsStateWithLifecycle()
    val loading by browse.loading.collectAsStateWithLifecycle()
    val error by browse.error.collectAsStateWithLifecycle()
    val state by player.state.collectAsStateWithLifecycle()

    var searchText by remember { mutableStateOf("") }
    var activeGenre by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(activeGenre) {
        browse.load(null, activeGenre)
    }

    Box(Modifier.fillMaxSize()) {

        Column(Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Free Music",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onChangeKey) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Change API key",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Gaana, artist ya album dhoondein") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { browse.load(searchText, activeGenre) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )

            // Genres
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GENRES) { genre ->
                    FilterChip(
                        selected = activeGenre == genre.second,
                        onClick = {
                            activeGenre = genre.second
                            if (searchText.isNotBlank()) browse.load(searchText, genre.second)
                        },
                        label = { Text(genre.first) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Content
            Box(Modifier.weight(1f)) {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )

                    error != null -> Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error ?: "",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { browse.load(searchText, activeGenre) }) {
                            Text("Dobara koshish karein")
                        }
                    }

                    else -> LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp
                        )
                    ) {
                        itemsIndexed(tracks) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrent = state.title == track.title && state.artist == track.artist,
                                onClick = {
                                    player.play(tracks, index)
                                    showPlayer = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Mini player
        if (state.hasItem && !showPlayer) {
            MiniPlayer(
                state = state,
                onClick = { showPlayer = true },
                onPlayPause = { player.togglePlayPause() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Full player
        if (showPlayer) {
            PlayerScreen(
                state = state,
                onClose = { showPlayer = false },
                onPlayPause = { player.togglePlayPause() },
                onNext = { player.next() },
                onPrev = { player.previous() },
                onSeek = { player.seekTo(it) },
                onShuffle = { player.toggleShuffle() },
                onRepeat = { player.toggleRepeat() }
            )
        }
    }
}

// ---------------------------------------------------------------- components

@Composable
private fun TrackRow(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = fmt(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MiniPlayer(
    state: PlayerUiState,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = state.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = state.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) PlayerIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play / pause"
                )
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    state: PlayerUiState,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val shownPosition = drag?.let { (it * duration).toLong() } ?: state.positionMs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(12.dp))

        AsyncImage(
            model = state.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(Modifier.height(30.dp))

        Text(
            text = state.title.ifBlank { "Kuch nahi chal raha" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(24.dp))

        Slider(
            value = (shownPosition.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { onSeek((it * duration).toLong()) }
                drag = null
            }
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(fmt(shownPosition), style = MaterialTheme.typography.labelMedium)
            Text(fmt(state.durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffle) {
                Icon(
                    PlayerIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPrev) {
                Icon(
                    PlayerIcons.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(38.dp)
                )
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(70.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) PlayerIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play / pause",
                    modifier = Modifier.size(38.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    PlayerIcons.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(38.dp)
                )
            }
            IconButton(onClick = onRepeat) {
                Icon(
                    PlayerIcons.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatAll) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.isBuffering) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        }
    }
}

private fun fmt(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
