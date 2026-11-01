package com.freemusic.player.ui

import android.app.Application
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.freemusic.player.data.Audius
import com.freemusic.player.data.InternetArchive
import com.freemusic.player.data.Track
import com.freemusic.player.player.PlayerUiState
import com.freemusic.player.player.PlayerViewModel
import com.freemusic.player.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MusicSource(val label: String) {
    ARCHIVE("Archive"),
    AUDIUS("Audius")
}

private val SOURCES = listOf(MusicSource.ARCHIVE, MusicSource.AUDIUS)

// ---------------------------------------------------------------- entry point

@Composable
fun App() {
    MainScreen()
}

// ---------------------------------------------------------------- browse VM

class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _pendingPlay = MutableStateFlow<Track?>(null)
    val pendingPlay = _pendingPlay.asStateFlow()

    private var job: Job? = null

    fun load(source: MusicSource, query: String?) {
        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val list = when (source) {
                    MusicSource.ARCHIVE -> InternetArchive.fetch(query)
                    MusicSource.AUDIUS -> Audius.fetch(query, null)
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

    /** Archive audio URLs are only known per item, so resolve first, then play. */
    fun resolveArchive(track: Track) {
        viewModelScope.launch {
            try {
                val url = InternetArchive.resolveAudioUrl(track.id)
                if (url == null) {
                    _error.value = "Is item mein MP3 file nahi mili."
                } else {
                    _pendingPlay.value = track.copy(audioUrl = url)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Archive error"
            }
        }
    }

    fun consumePendingPlay() {
        _pendingPlay.value = null
    }
}

// ---------------------------------------------------------------- main screen

@Composable
private fun MainScreen() {

    val browse: BrowseViewModel = viewModel()
    val player: PlayerViewModel = viewModel()

    val tracks by browse.tracks.collectAsStateWithLifecycle()
    val loading by browse.loading.collectAsStateWithLifecycle()
    val error by browse.error.collectAsStateWithLifecycle()
    val state by player.state.collectAsStateWithLifecycle()
    val pendingPlay by browse.pendingPlay.collectAsStateWithLifecycle()

    var source by remember { mutableStateOf(MusicSource.ARCHIVE) }
    var searchText by remember { mutableStateOf("") }
    var showNowPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        browse.load(source, null)
    }

    LaunchedEffect(pendingPlay) {
        pendingPlay?.let { ready ->
            player.play(listOf(ready), 0)
            showNowPlaying = true
            browse.consumePendingPlay()
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {

        AmbientBackground()

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { HeroHeader() }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchPill(
                    value = searchText,
                    onValueChange = { searchText = it },
                    onSearch = { browse.load(source, searchText) },
                    onClear = {
                        searchText = ""
                        browse.load(source, null)
                    }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SegmentedControl(selected = source, onSelect = { source = it })
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    title = if (searchText.isBlank()) "Trending now" else "Results",
                    count = tracks.size
                )
            }

            when {
                loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Palette.Primary) }
                }

                error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error.orEmpty(),
                            color = Palette.Muted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { browse.load(source, searchText) }) {
                            Text("Dobara koshish karein", color = Palette.Primary)
                        }
                    }
                }

                else -> items(count = tracks.size) { index ->
                    val track = tracks[index]
                    ArtworkCard(
                        track = track,
                        playing = state.title == track.title && state.artist == track.artist,
                        onClick = {
                            if (source == MusicSource.ARCHIVE) {
                                browse.resolveArchive(track)
                            } else {
                                player.play(tracks, index)
                                showNowPlaying = true
                            }
                        }
                    )
                }
            }
        }

        if (state.hasItem && !showNowPlaying) {
            MiniPlayer(
                state = state,
                onOpen = { showNowPlaying = true },
                onPlayPause = { player.togglePlayPause() },
                onNext = { player.next() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showNowPlaying) {
            NowPlayingScreen(
                state = state,
                onClose = { showNowPlaying = false },
                onPlayPause = { player.togglePlayPause() },
                onNext = { player.next() },
                onPrev = { player.previous() },
                onSeek = { player.seekTo(it) }
            )
        }
    }
}

// ---------------------------------------------------------------- background

@Composable
private fun AmbientBackground() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )

    Canvas(Modifier.fillMaxSize()) {
        drawRect(Palette.Bg)
        val w = size.width
        val h = size.height

        fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
        }

        blob(Color(0xFF6D3BFF), w * (0.10f + 0.10f * drift), h * 0.06f, w * 1.05f)
        blob(Color(0xFF00D9B5), w * (0.95f - 0.12f * drift), h * 0.30f, w * 0.95f)
        blob(Color(0xFFFF4D9D), w * (0.30f + 0.10f * drift), h * 0.92f, w * 0.9f)
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Palette.GlassTop, Palette.GlassBottom))
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0x59FFFFFF), Color(0x14FFFFFF))
                ),
                shape = shape
            ),
        content = content
    )
}

@Composable
private fun HeroHeader() {
    Column(Modifier.padding(top = 18.dp, bottom = 4.dp)) {
        Text("Hello", color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "What do you\nwant to hear?",
            color = Palette.Text,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Palette.Muted,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        "Search songs, artists or albums",
                        color = Palette.Muted.copy(alpha = 0.7f),
                        fontSize = 13.5.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.Text, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(Palette.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = Palette.Muted,
                    modifier = Modifier.size(18.dp).clickable { onClear() }
                )
            }
        }
    }
}

@Composable
private fun SegmentedControl(selected: MusicSource, onSelect: (MusicSource) -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(5.dp)) {
            SOURCES.forEach { src ->
                val active = src == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (active) {
                                Brush.linearGradient(listOf(Palette.Primary, Color(0xFF6D3BFF)))
                            } else {
                                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .clickable { onSelect(src) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = src.label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) Color.White else Palette.Muted
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = Palette.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text("$count", color = Palette.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ArtworkCard(track: Track, playing: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(14.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0x38FFFFFF), Color(0x0FFFFFFF)))
                )
        ) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // gloss sweep, like the glass cards in the design
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0x40FFFFFF), Color(0x00FFFFFF)),
                            start = Offset.Zero,
                            end = Offset(360f, 360f)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x4DFFFFFF))
                    .border(1.dp, Color(0x59FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (playing) {
                    Equalizer()
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(
            text = track.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (playing) Palette.Primary else Palette.Text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = track.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Palette.Muted,
            fontSize = 11.5.sp
        )
    }
}

@Composable
private fun Equalizer() {
    val transition = rememberInfiniteTransition(label = "eq")
    val a by transition.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val b by transition.animateFloat(
        0.5f, 0.9f,
        infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "b"
    )
    val c by transition.animateFloat(
        0.25f, 0.8f,
        infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "c"
    )

    Row(
        Modifier.height(15.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(a, b, c).forEach { level ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(level.coerceIn(0.1f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun MiniPlayer(
    state: PlayerUiState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = state.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1FFFFFFF))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        color = Palette.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.artist,
                        color = Palette.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) PlayerIcons.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play / pause",
                        tint = Palette.Text
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        PlayerIcons.SkipNext,
                        contentDescription = "Next",
                        tint = Palette.Text
                    )
                }
            }

            val progress = if (state.durationMs > 0) {
                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            } else 0f

            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x1FFFFFFF))) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Palette.Primary)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingScreen(
    state: PlayerUiState,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val shown = drag?.let { (it * duration).toLong() } ?: state.positionMs

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {

        AsyncImage(
            model = state.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.30f)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Palette.Bg.copy(alpha = 0.55f),
                        Palette.Bg.copy(alpha = 0.90f),
                        Palette.Bg
                    )
                )
            )
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = Palette.Text
                    )
                }
                Text(
                    text = "Now Playing",
                    color = Palette.Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(20.dp))

            AsyncImage(
                model = state.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(26.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x1FFFFFFF))
            )

            Spacer(Modifier.height(30.dp))

            Text(
                text = state.title.ifBlank { "Kuch nahi chal raha" },
                color = Palette.Text,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.artist,
                color = Palette.Muted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(18.dp))

            Slider(
                value = (shown.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { onSeek((it * duration).toLong()) }
                    drag = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Primary,
                    activeTrackColor = Palette.Primary,
                    inactiveTrackColor = Color(0x33FFFFFF)
                )
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(fmt(shown), color = Palette.Muted, fontSize = 11.sp)
                Text(fmt(state.durationMs), color = Palette.Muted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(22.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev) {
                    Icon(
                        PlayerIcons.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Palette.Text,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Palette.Primary, Color(0xFF6D3BFF)))
                        )
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) PlayerIcons.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play / pause",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        PlayerIcons.SkipNext,
                        contentDescription = "Next",
                        tint = Palette.Text,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
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
