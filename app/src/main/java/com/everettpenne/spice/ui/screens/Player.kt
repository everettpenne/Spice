package com.everettpenne.spice.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.data.model.PlaybackReport
import com.everettpenne.spice.data.model.TICKS_PER_SECOND
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.components.*
import com.everettpenne.spice.ui.glass.GlassButton
import com.everettpenne.spice.ui.glass.GlassSheet
import com.everettpenne.spice.ui.theme.Spice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Loads the item, then plays it full screen in landscape. */
@Composable
fun PlayerScreen(id: String, onBack: () -> Unit, onPlayNext: (String) -> Unit) {
    val graph = LocalContext.current.graph
    val load = rememberLoad(id) { graph.api.item(id) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = load.state) {
            Load.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            is Load.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.failure.message, color = Color.White); GlassButton("Back", onBack)
            }
            is Load.Done -> PlayerContent(s.value, onBack, onPlayNext)
        }
    }
}

private fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(item: JfItem, onBack: () -> Unit, onPlayNext: (String) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val view = LocalView.current
    val source = item.mediaSources.firstOrNull()
    val settings = graph.settings
    val playSessionId = remember(item.id) { UUID.randomUUID().toString().replace("-", "") }
    val startMs = remember(item.id) { if (item.progress != null) item.userData.positionTicks / (TICKS_PER_SECOND / 1000) else 0L }
    var transcoding by remember(item.id) { mutableStateOf(settings.forceTranscode.value || !settings.directPlay.value || source?.supportsDirectPlay == false && source.supportsDirectStream.not()) }
    var failed by remember(item.id) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(startMs) }
    var duration by remember { mutableLongStateOf((item.runTimeTicks ?: 0L) / (TICKS_PER_SECOND / 1000)) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var buffering by remember { mutableStateOf(true) }
    val detached = remember { CoroutineScope(Dispatchers.IO) }

    // Landscape, full-bleed, screen kept awake: put everything back when leaving.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = previous
        }
    }

    val player = remember(item.id) {
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(graph.http))).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
    }

    fun report(paused: Boolean = !player.isPlaying): PlaybackReport = PlaybackReport(
        itemId = item.id, mediaSourceId = source?.id, positionTicks = player.currentPosition.coerceAtLeast(0) * (TICKS_PER_SECOND / 1000),
        playSessionId = playSessionId, playMethod = if (transcoding) "Transcode" else "DirectStream", isPaused = paused)

    // Sets the stream: the original file, or (after a failure, or by choice) the server's HLS transcode, resuming where playback was.
    LaunchedEffect(item.id, transcoding) {
        val src = source ?: return@LaunchedEffect
        val url = (if (transcoding) graph.urls.hlsStream(item.id, src.id, playSessionId) else graph.urls.directStream(item.id, src.id)) ?: return@LaunchedEffect
        val resumeAt = if (player.currentMediaItem != null) player.currentPosition else startMs
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (resumeAt > 0) player.seekTo(resumeAt)
        player.playWhenReady = true
    }

    DisposableEffect(player) {
        var started = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    if (player.duration != C.TIME_UNSET) duration = player.duration
                    if (!started) { started = true; val r = report(false); detached.launch { graph.api.reportStart(r) } }
                }
                if (state == Player.STATE_ENDED) {
                    val finished = report(true)
                    detached.launch {
                        graph.api.reportStopped(finished); graph.api.setPlayed(item.id, true)
                        val next = if (item.isEpisode && item.seriesId != null && settings.autoplayNext.value)
                            (graph.api.episodes(item.seriesId, null) as? ApiResult.Ok)?.value?.let { eps -> eps.getOrNull(eps.indexOfFirst { it.id == item.id } + 1) } else null
                        kotlinx.coroutines.withContext(Dispatchers.Main) { if (next != null) onPlayNext(next.id) else onBack() }
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) { if (!transcoding) transcoding = true else failed = true }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (started) { val r = report(true); detached.launch { graph.api.reportStopped(r) } }
            player.release()
        }
    }

    // The clock the controls read, and a progress report every ten seconds so the server (and other devices) know where you are.
    LaunchedEffect(player) {
        var lastReport = 0L
        while (true) {
            delay(500)
            if (scrubbing == null) position = player.currentPosition.coerceAtLeast(0)
            val now = System.currentTimeMillis()
            if (player.isPlaying && now - lastReport > 10_000) { lastReport = now; val r = report(false); detached.launch { graph.api.reportProgress(r) } }
        }
    }
    LaunchedEffect(controlsVisible, playing, showTracks, scrubbing) {
        if (controlsVisible && playing && !showTracks && scrubbing == null) { delay(4000); controlsVisible = false }
    }

    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { controlsVisible = !controlsVisible }) {
        AndroidView(factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false; this.player = player; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; setShutterBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }, modifier = Modifier.fillMaxSize())
        if (buffering && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        if (failed) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This couldn't be played, even transcoded. Check the file on the server.", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            GlassButton("Back", onBack)
        }

        if (controlsVisible && !failed) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .75f)))))
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerButton(Icons.Rounded.ArrowBack, "Back", onBack)
                Column(Modifier.weight(1f)) {
                    Text(item.seriesName ?: item.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.isEpisode) Text(listOfNotNull(item.episodeLabel, item.name).joinToString(" · "), color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Text(if (transcoding) "Transcoding" else "Direct", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.background(Color.White.copy(alpha = .14f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
                PlayerButton(Icons.Rounded.Subtitles, "Audio and subtitles", { showTracks = true })
            }
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerButton(Icons.Rounded.Replay10, "Back 10 seconds", { player.seekBack() }, size = 56)
                PlayerButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", { if (player.isPlaying) player.pause() else player.play() }, size = 76)
                PlayerButton(Icons.Rounded.Forward10, "Forward 10 seconds", { player.seekForward() }, size = 56)
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatClock(scrubbing?.let { (it * duration).toLong() } ?: position), color = Color.White, style = MaterialTheme.typography.bodySmall)
                Slider(value = scrubbing ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { scrubbing = it; controlsVisible = true },
                    onValueChangeFinished = { scrubbing?.let { player.seekTo((it * duration).toLong()); position = (it * duration).toLong() }; scrubbing = null },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Seek" },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Spice.accent, inactiveTrackColor = Color.White.copy(alpha = .3f)))
                Text("-" + formatClock((duration - (scrubbing?.let { (it * duration).toLong() } ?: position)).coerceAtLeast(0)), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (showTracks) TrackSheet(player) { showTracks = false }
    }
}

@Composable
private fun PlayerButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Int = 44) {
    Box(Modifier.size(size.dp).background(Color.Black.copy(alpha = .4f), CircleShape).clickable(onClick = onClick).semantics { contentDescription = description }, Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * .55f).dp))
    }
}

/** Lists the audio and subtitle tracks the stream offers and applies the pick. Subtitles default to off. */
@OptIn(UnstableApi::class)
@Composable
private fun TrackSheet(player: Player, onDismiss: () -> Unit) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onTracksChanged(t: androidx.media3.common.Tracks) { tracks = t } }
        player.addListener(l); onDispose { player.removeListener(l) }
    }
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || text.none { it.isSelected }

    fun label(g: androidx.media3.common.Tracks.Group, n: Int): String {
        val f = g.getTrackFormat(0)
        return listOfNotNull(f.label ?: f.language?.let { java.util.Locale(it).displayLanguage }, f.codecs?.takeIf { f.label == null && f.language == null }).joinToString(" · ").ifEmpty { "Track ${n + 1}" }
    }

    GlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Subtitles", style = MaterialTheme.typography.titleMedium)
            TrackRow("Off", textOff) { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() }
            text.forEachIndexed { i, g -> TrackRow(label(g, i), g.isSelected && !textOff) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
            } }
            if (text.isEmpty()) Text("This stream has no subtitles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Audio", style = MaterialTheme.typography.titleMedium)
            audio.forEachIndexed { i, g -> TrackRow(label(g, i), g.isSelected) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
            } }
            if (audio.size <= 1) Text("Only one audio track.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TrackRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = if (selected) Spice.accent else MaterialTheme.colorScheme.onSurface)
        if (selected) Icon(Icons.Rounded.Check, null, tint = Spice.accent)
    }
}
