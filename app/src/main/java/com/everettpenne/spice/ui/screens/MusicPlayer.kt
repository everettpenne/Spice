package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.everettpenne.glass.*
import com.everettpenne.spice.playback.PlaybackMeta
import com.everettpenne.spice.playback.PlaybackStatus
import com.everettpenne.spice.ui.components.*
import com.everettpenne.spice.ui.theme.Spice
import kotlinx.coroutines.delay

/** The song the service is playing right now (or nothing), and whether it is moving. */
data class NowPlaying(val item: MediaItem?, val isPlaying: Boolean)

/** Follows the service's current media item and playing state, so the mini-player and the now-playing screen stay in step with it. */
@Composable
fun rememberNowPlaying(controller: MediaController?): NowPlaying {
    var item by remember(controller) { mutableStateOf(controller?.currentMediaItem) }
    var playing by remember(controller) { mutableStateOf(controller?.isPlaying == true) }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        item = c.currentMediaItem; playing = c.isPlaying
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { item = mediaItem }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }
    return NowPlaying(item, playing)
}

/**
 * The strip above the tab bar while a song plays: artwork, title, artist, and pause/next. Shown by the shell (App.kt) whenever the service is
 * on an audio item, so it follows you across every tab; tapping it opens the full now-playing screen. It reads [PlaybackStatus] (not a
 * controller) to know whether to appear, and only connects a controller for its buttons while a song is actually playing.
 */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val status by PlaybackStatus.now.collectAsState()
    val item = status.item
    if (item == null || PlaybackMeta.itemType(item) != "Audio") return
    MiniPlayerBar(item, status.isPlaying, onOpen)
}

@Composable
private fun MiniPlayerBar(item: MediaItem, isPlaying: Boolean, onOpen: () -> Unit) {
    val controller = rememberMediaController()
    val meta = item.mediaMetadata
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
        .frostedGlass(RoundedCornerShape(percent = 50), lensHeight = 12f, lensAmount = 24f)
        .clickable(onClick = onOpen).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) {
            ItemImage(meta.artworkUri?.toString(), Modifier.fillMaxSize(), contentDescription = null, placeholderIcon = Icons.Rounded.MusicNote)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(meta.title?.toString().orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta.artist?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GlassIconButton(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play",
            { if (isPlaying) controller?.pause() else controller?.play() }, size = 44)
        GlassIconButton(Icons.Rounded.SkipNext, "Next", { controller?.seekToNextMediaItem() }, size = 44)
    }
}

/** The full now-playing screen: artwork, track/artist/album, transport, and a scrubber. It drives the service, not its own player. */
@Composable
fun NowPlayingScreen(onBack: () -> Unit) {
    val controller = rememberMediaController()
    val now = rememberNowPlaying(controller)
    GlassScreen(bars = { GlassCenteredTopBar("Now Playing", onBack, Modifier.align(Alignment.TopCenter)) }) {
        when {
            controller == null -> LoadingRow()
            now.item == null || PlaybackMeta.itemType(now.item) != "Audio" ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Rounded.MusicNote, "Nothing playing", "Play something from the Music tab and it will show up here.")
                }
            else -> NowPlayingContent(controller, now)
        }
    }
}

@Composable
private fun NowPlayingContent(controller: MediaController, now: NowPlaying) {
    val item = now.item!!
    val meta = item.mediaMetadata
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var shuffle by remember { mutableStateOf(controller.shuffleModeEnabled) }

    LaunchedEffect(controller) {
        while (true) {
            if (scrubbing == null) position = controller.currentPosition.coerceAtLeast(0)
            if (controller.duration != C.TIME_UNSET && controller.duration > 0) duration = controller.duration
            delay(500)
        }
    }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    Column(Modifier.fillMaxSize().padding(top = topContentPadding(), bottom = bottomContentPadding(), start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp))) {
            ItemImage(meta.artworkUri?.toString(), Modifier.fillMaxSize(), contentDescription = null, placeholderIcon = Icons.Rounded.MusicNote)
        }
        Spacer(Modifier.height(28.dp))
        Text(meta.title?.toString().orEmpty(), style = IosType.title2.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta.artist?.toString().orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        meta.albumTitle?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(20.dp))
        GlassSlider(
            value = scrubbing ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { f -> controller.seekTo((f * duration).toLong()); position = (f * duration).toLong() }; scrubbing = null
            },
            modifier = Modifier.fillMaxWidth(), description = "Seek")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClockMs(position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-" + formatClockMs((duration - position).coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.Shuffle, if (shuffle) "Shuffle on" else "Shuffle off",
                { controller.shuffleModeEnabled = !controller.shuffleModeEnabled }, tint = if (shuffle) Spice.accent else null, size = 46)
            GlassIconButton(Icons.Rounded.SkipPrevious, "Previous", { controller.seekToPreviousMediaItem() }, size = 52)
            GlassIconButton(if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (now.isPlaying) "Pause" else "Play",
                { if (now.isPlaying) controller.pause() else controller.play() }, size = 68)
            GlassIconButton(Icons.Rounded.SkipNext, "Next", { controller.seekToNextMediaItem() }, size = 52)
            Spacer(Modifier.size(46.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}

private fun formatClockMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
