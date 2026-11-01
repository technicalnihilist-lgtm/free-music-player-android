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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.freemusic.player.data.Audius
import com.freemusic.player.data.CcMixter
import com.freemusic.player.data.InternetArchive
import com.freemusic.player.data.Jamendo
import com.freemusic.player.data.Prefs
import com.freemusic.player.data.RadioBrowser
import com.freemusic.player.data.SpotifyApi
import com.freemusic.player.data.SpotifyAuth
import com.freemusic.player.data.Track
import com.freemusic.player.player.PlayerUiState
import com.freemusic.player.player.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MusicSource(val label: String) {
    ARCHIVE("Archive"),
    AUDIUS("Audius"),
    JAMENDO("Jamendo"),
    CCMIXTER("ccMixter"),
    MUSOPEN("Musopen"),
    RADIO("Radio"),
    SPOTIFY("Spotify")
}

// Only free / open catalogues are exposed. Jamendo and Spotify code stays in the
// repo but is not shown in the UI.
private val SOURCES = listOf(
    MusicSource.ARCHIVE,
    MusicSource.AUDIUS
)

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
    MainScreen()
}

// ---------------------------------------------------------------- first launch

@Composable
private fun KeyCard(onSave: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp)
    ) {
        Text(
            text = "Jamendo key chahiye",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Jamendo se Creative Commons music chalane ke liye ek free API key chahiye — " +
                "2 minute ka kaam hai. Audius tab bina key ke chalta rehta hai.",
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
            Text("Key save karein")
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

    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    private val _pendingPlay = MutableStateFlow<Track?>(null)
    val pendingPlay = _pendingPlay.asStateFlow()

    private var job: Job? = null

    /** Archive audio URLs are only known per item, so resolve first, then play. */
    fun resolveArchive(track: Track) {
        viewModelScope.launch {
            _notice.value = "Load ho raha hai…"
            _notice.value = try {
                val url = InternetArchive.resolveAudioUrl(track.id)
                if (url == null) {
                    "Is item mein MP3 file nahi mili."
                } else {
                    _pendingPlay.value = track.copy(audioUrl = url)
                    null
                }
            } catch (e: Exception) {
                e.message ?: "Archive error"
            }
        }
    }

    fun consumePendingPlay() {
        _pendingPlay.value = null
    }

    /** Spotify audio never enters this app — we ask the Spotify app to play it. */
    fun playOnSpotify(track: Track) {
        viewModelScope.launch {
            _notice.value = "Spotify ko bhej rahe hain…"
            val ctx = getApplication<Application>()
            val token = SpotifyAuth.accessToken(ctx)
            if (token == null) {
                _notice.value = "Pehle Spotify se login karein."
                return@launch
            }
            _notice.value = try {
                SpotifyApi.playTrack(token, "spotify:track:${track.id}")
            } catch (e: Exception) {
                e.message ?: "Spotify error"
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun load(source: MusicSource, query: String?, tag: String?) {
        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val list = when (source) {
                    MusicSource.AUDIUS -> Audius.fetch(query, tag)
                    MusicSource.JAMENDO -> {
                        val clientId = Prefs.clientId(getApplication())
                        if (clientId.isBlank()) {
                            _error.value = "Jamendo ke liye API key chahiye."
                            return@launch
                        }
                        Jamendo.fetch(clientId, query, tag)
                    }
                    MusicSource.SPOTIFY -> {
                        val token = SpotifyAuth.accessToken(getApplication())
                        if (token == null) {
                            _error.value = "Spotify se login karein."
                            return@launch
                        }
                        SpotifyApi.search(token, query.orEmpty())
                    }
                    MusicSource.ARCHIVE -> InternetArchive.fetch(query)
                    MusicSource.MUSOPEN -> InternetArchive.fetch(query, "musopen")
                    MusicSource.CCMIXTER -> CcMixter.fetch(query)
                    MusicSource.RADIO -> RadioBrowser.fetch(query)
                }
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
private fun MainScreen() {

    val context = LocalContext.current
    val browse: BrowseViewModel = viewModel()
    val player: PlayerViewModel = viewModel()

    val tracks by browse.tracks.collectAsStateWithLifecycle()
    val loading by browse.loading.collectAsStateWithLifecycle()
    val error by browse.error.collectAsStateWithLifecycle()
    val state by player.state.collectAsStateWithLifecycle()

    var searchText by remember { mutableStateOf("") }
    var activeGenre by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(MusicSource.ARCHIVE) }
    var jamendoKey by remember { mutableStateOf(Prefs.clientId(context)) }
    var showKey by remember { mutableStateOf(false) }
    var spotifyReady by remember { mutableStateOf(Prefs.spotifyRefreshToken(context).isNotBlank()) }
    val sessionVersion by SpotifyAuth.sessionVersion.collectAsStateWithLifecycle()
    val notice by browse.notice.collectAsStateWithLifecycle()
    val pendingPlay by browse.pendingPlay.collectAsStateWithLifecycle()

    // Archive cards resolve their MP3 first, then hand it to the player.
    LaunchedEffect(pendingPlay) {
        pendingPlay?.let { ready ->
            player.play(listOf(ready), 0)
            showPlayer = true
            browse.consumePendingPlay()
        }
    }

    LaunchedEffect(sessionVersion) {
        spotifyReady = Prefs.spotifyRefreshToken(context).isNotBlank()
        if (spotifyReady && source == MusicSource.SPOTIFY) browse.load(source, null, activeGenre)
    }

    LaunchedEffect(source, activeGenre, jamendoKey) {
        if (source == MusicSource.JAMENDO && jamendoKey.isBlank()) {
            showKey = true
        } else {
            showKey = false
            browse.load(source, null, activeGenre)
        }
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Free Music",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = source.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    onSearch = { browse.load(source, searchText, activeGenre) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )

            // Source switcher
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SOURCES) { s ->
                    FilterChip(
                        selected = source == s,
                        onClick = { source = s },
                        label = { Text(s.label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Content
            Box(Modifier.weight(1f)) {
                when {
                    showKey -> KeyCard(onSave = { id ->
                        Prefs.setClientId(context, id)
                        jamendoKey = id.trim()
                    })

                    source == MusicSource.SPOTIFY && !spotifyReady -> SpotifyCard(
                        onStart = { clientId ->
                            Prefs.setSpotifyClientId(context, clientId)
                            val verifier = SpotifyAuth.newVerifier()
                            val authState = SpotifyAuth.newState()
                            Prefs.setSpotifyPending(context, verifier, authState)
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(SpotifyAuth.authorizeUrl(clientId, verifier, authState))
                                    )
                                )
                            }
                        }
                    )

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
                        Button(onClick = { browse.load(source, searchText, activeGenre) }) {
                            Text("Dobara koshish karein")
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp, top = 4.dp, bottom = 100.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(count = tracks.size) { index ->
                            val track = tracks[index]
                            ArtworkCard(
                                track = track,
                                isCurrent = state.title == track.title && state.artist == track.artist,
                                onClick = {
                                    when (source) {
                                        MusicSource.SPOTIFY -> browse.playOnSpotify(track)
                                        MusicSource.ARCHIVE, MusicSource.MUSOPEN ->
                                            browse.resolveArchive(track)
                                        else -> {
                                            player.play(tracks, index)
                                            showPlayer = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Source notices (Spotify playback, Archive resolving, errors)
        run {
            notice?.let { message ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { browse.clearNotice() }) { Text("OK") }
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

@Composable
private fun ArtworkCard(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
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
}

@Composable
private fun SpotifyCard(onStart: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(Prefs.spotifyClientId(context)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Spotify se jodein",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Gaane Spotify app ke andar bajenge, is liye phone mein Spotify app aur " +
                "account par Premium chahiye. Audio hamari app mein nahi aata — ye catalog " +
                "browse karke Spotify ko play command bhejti hai.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Spotify Client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Dashboard mein redirect URI ye add karein:\n" + SpotifyAuth.REDIRECT_URI,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { if (text.isNotBlank()) onStart(text.trim()) },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login with Spotify")
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
