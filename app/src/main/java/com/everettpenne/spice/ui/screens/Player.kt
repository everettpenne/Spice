package com.everettpenne.spice.ui.screens

import com.everettpenne.glass.GlassSlider
import com.everettpenne.glass.pressSwell
import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.data.model.TICKS_PER_SECOND
import com.everettpenne.spice.graph
import com.everettpenne.spice.playback.PiP
import com.everettpenne.spice.playback.PlaybackMeta
import com.everettpenne.spice.playback.PlaybackService
import com.everettpenne.spice.ui.components.*
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassSheet
import com.everettpenne.glass.IosType
import com.everettpenne.spice.ui.theme.Spice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Connects to the playback service, which owns the player: the screen only drives it, so playback does not depend on the screen being alive. */
@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future); controller = null }
    }
    return controller
}

/** Loads the item, then shows the player: video on top and the details below in portrait, full screen in landscape. */
@Composable
fun PlayerScreen(id: String, nav: Navigator, onBack: () -> Unit, onSwitch: (String) -> Unit) {
    val graph = LocalContext.current.graph
    val load = rememberLoad(id) { graph.api.item(id) }
    when (val s = load.state) {
        Load.Loading -> Box(Modifier.fillMaxSize().background(Color.Black)) { CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White) }
        is Load.Error -> Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            Text(s.failure.message, color = Color.White); GlassButton("Back", onBack)
        }
        is Load.Done -> PlayerContent(s.value, nav, onBack, onSwitch)
    }
}

private fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** True when the file has audio but this phone can decode none of it (DTS and TrueHD are the usual ones): the video would play in silence. */
private fun Tracks.audioUnplayable(): Boolean = groups.filter { it.type == C.TRACK_TYPE_AUDIO }.let { audio -> audio.isNotEmpty() && audio.none { it.isSupported(false) } }
private fun Tracks.videoUnplayable(): Boolean = groups.filter { it.type == C.TRACK_TYPE_VIDEO }.let { video -> video.isNotEmpty() && video.none { it.isSupported(false) } }

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(item: JfItem, nav: Navigator, onBack: () -> Unit, onSwitch: (String) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val view = LocalView.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val source = item.mediaSources.firstOrNull()
    val settings = graph.settings
    val controller = rememberMediaController()
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
    var notice by remember(item.id) { mutableStateOf<String?>(null) }
    val pipActive by PiP.active.collectAsState()

    // The first time, ask to show the playback notification (Android 13 and newer); sound in the background works either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Landscape is full screen with the system bars tucked away; portrait keeps them, with light icons over the black video.
    DisposableEffect(landscape) {
        val controllerCompat = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controllerCompat?.isAppearanceLightStatusBars ?: false
        controllerCompat?.isAppearanceLightStatusBars = false
        if (landscape) {
            controllerCompat?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controllerCompat?.hide(WindowInsetsCompat.Type.systemBars())
        }
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; controllerCompat?.show(WindowInsetsCompat.Type.systemBars()); controllerCompat?.isAppearanceLightStatusBars = wasLight }
    }
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }

    fun toggleFullscreen() {
        activity ?: return
        if (landscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            scope.launch { delay(1500); activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }   // then follow the sensor again
        } else activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /** Ends playback, but only if what is playing is this item: a screen being replaced by the next episode's must not stop the next episode. */
    fun stopPlayback() {
        controller?.let { c -> if (PlaybackMeta.itemId(c.currentMediaItem) == item.id) { c.stop(); c.clearMediaItems() } }
    }
    fun leave() { stopPlayback(); onBack() }
    BackHandler { leave() }
    DisposableEffect(controller) { onDispose { stopPlayback() } }

    // Hands the stream to the service: the original file, or the server's HLS transcode (after a failure, or by choice), resuming where playback was.
    LaunchedEffect(controller, item.id, transcoding) {
        val c = controller ?: return@LaunchedEffect
        val src = source ?: return@LaunchedEffect
        val method = if (transcoding) "Transcode" else "DirectStream"
        val current = c.currentMediaItem
        if (PlaybackMeta.itemId(current) == item.id && PlaybackMeta.method(current) == method) return@LaunchedEffect   // already playing this: rejoin it
        val url = (if (transcoding) graph.urls.hlsStream(item.id, src.id, playSessionId) else graph.urls.directStream(item.id, src.id)) ?: return@LaunchedEffect
        val resumeAt = if (PlaybackMeta.itemId(current) == item.id) c.currentPosition else startMs
        val metadata = MediaMetadata.Builder().setTitle(if (item.isEpisode) item.name else item.name)
            .setArtist(item.seriesName).setSubtitle(item.episodeLabel).setExtras(PlaybackMeta.extras(item.id, src.id, method, playSessionId, item.type))
            .apply { graph.urls.poster(item, 400)?.let { setArtworkUri(Uri.parse(it)) } }.build()
        c.setMediaItem(MediaItem.Builder().setMediaId(item.id).setUri(url).setMediaMetadata(metadata)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(url)).build()).build(), resumeAt)
        c.volume = 1f
        c.prepare(); c.playWhenReady = true
    }

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        playing = c.isPlaying; buffering = c.playbackState == Player.STATE_BUFFERING
        if (c.duration != C.TIME_UNSET && c.duration > 0) duration = c.duration
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && c.duration != C.TIME_UNSET) duration = c.duration
                if (state == Player.STATE_ENDED && PlaybackMeta.itemId(c.currentMediaItem) == item.id) scope.launch {
                    val next = if (item.isEpisode && item.seriesId != null && settings.autoplayNext.value)
                        (graph.api.episodes(item.seriesId, null) as? ApiResult.Ok)?.value?.let { eps -> eps.getOrNull(eps.indexOfFirst { it.id == item.id } + 1) } else null
                    if (next != null) onSwitch(next.id) else leave()
                }
            }
            // A file the phone cannot play (or can play without its sound) is not left broken: ask the server to transcode it instead.
            override fun onTracksChanged(tracks: Tracks) {
                if (transcoding || c.currentMediaItem?.let { PlaybackMeta.itemId(it) } != item.id) return
                if (tracks.audioUnplayable()) { notice = "Converting the audio on the server"; transcoding = true }
                else if (tracks.videoUnplayable()) { notice = "Converting the video on the server"; transcoding = true }
            }
            override fun onPlayerError(error: PlaybackException) { if (!transcoding) transcoding = true else failed = true }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    // The clock the controls read; it only runs while they are showing.
    LaunchedEffect(controller, controlsVisible) {
        val c = controller ?: return@LaunchedEffect
        while (controlsVisible) {
            if (scrubbing == null) position = c.currentPosition.coerceAtLeast(0)
            delay(500)
        }
    }
    LaunchedEffect(controlsVisible, playing, showTracks, scrubbing) {
        if (controlsVisible && playing && !showTracks && scrubbing == null) { delay(4000); controlsVisible = false }
    }
    LaunchedEffect(notice) { if (notice != null) { delay(6000); notice = null } }

    val video: @Composable (Boolean) -> Unit = { compact ->
        Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { controlsVisible = !controlsVisible }) {
            AndroidView(factory = { ctx ->
                PlayerView(ctx).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; setShutterBackgroundColor(android.graphics.Color.BLACK) }
            }, update = { it.player = controller }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
            if ((buffering || controller == null) && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center).size(if (compact) 32.dp else 44.dp), color = Color.White, strokeWidth = 3.dp)
            if (failed) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This couldn't be played, even converted. Check the file on the server.", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                GlassButton("Back", ::leave)
            }
            notice?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 52.dp else 72.dp)
                .background(Color.Black.copy(alpha = .6f), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) }
            if (controlsVisible && !failed && !pipActive) PlayerControls(
                compact = compact, landscape = landscape, title = item.seriesName ?: item.name,
                subtitle = if (item.isEpisode) listOfNotNull(item.episodeLabel, item.name).joinToString(" · ") else null,
                transcoding = transcoding, playing = playing, position = scrubbing?.let { (it * duration).toLong() } ?: position, duration = duration, scrub = scrubbing,
                onBack = ::leave, onTracks = { showTracks = true }, onFullscreen = ::toggleFullscreen,
                onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                onSeekBack = { controller?.seekBack() }, onSeekForward = { controller?.seekForward() },
                onScrub = { scrubbing = it; controlsVisible = true },
                onScrubDone = { scrubbing?.let { f -> controller?.seekTo((f * duration).toLong()); position = (f * duration).toLong() }; scrubbing = null })
        }
    }

    Box(Modifier.fillMaxSize().background(if (landscape || pipActive) Color.Black else MaterialTheme.colorScheme.background)) {
        // In the PiP window only the video shows: no controls overlay and no details below it.
        if (pipActive) {
            video(true)
        } else if (landscape) {
            video(false)
        } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding()) { Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { video(true) } }
            PlayerInfo(item, nav, onSwitch, ::stopPlayback)
        }
        if (showTracks && controller != null && !pipActive) TrackSheet(controller) { showTracks = false }
    }
}

@Composable
private fun PlayerControls(
    compact: Boolean, landscape: Boolean, title: String, subtitle: String?, transcoding: Boolean, playing: Boolean, position: Long, duration: Long, scrub: Float?,
    onBack: () -> Unit, onTracks: () -> Unit, onFullscreen: () -> Unit, onPlayPause: () -> Unit, onSeekBack: () -> Unit, onSeekForward: () -> Unit,
    onScrub: (Float) -> Unit, onScrubDone: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .75f)))))
        val safe = if (landscape) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().then(safe).padding(if (compact) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerButton(Icons.Rounded.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null && !compact) Text(subtitle, color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Text(if (transcoding) "Transcoding" else "Direct", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.background(Color.White.copy(alpha = .14f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
            PlayerButton(Icons.Rounded.Subtitles, "Audio and subtitles", onTracks)
        }
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 36.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerButton(Icons.Rounded.Replay10, "Back 10 seconds", onSeekBack, size = if (compact) 44 else 56)
            PlayerButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", onPlayPause, size = if (compact) 56 else 76)
            PlayerButton(Icons.Rounded.Forward10, "Forward 10 seconds", onSeekForward, size = if (compact) 44 else 56)
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(safe).padding(horizontal = if (compact) 12.dp else 24.dp, vertical = if (compact) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatClock(position), color = Color.White, style = MaterialTheme.typography.bodySmall)
            GlassSlider(value = scrub ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f, onValueChange = onScrub, onValueChangeFinished = onScrubDone,
                modifier = Modifier.weight(1f), trackColor = Color.White.copy(alpha = .3f), description = "Seek")
            Text("-" + formatClock((duration - position).coerceAtLeast(0)), color = Color.White, style = MaterialTheme.typography.bodySmall)
            Icon(if (landscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (landscape) "Exit full screen" else "Full screen", tint = Color.White,
                modifier = Modifier.size(40.dp).clickable(onClick = onFullscreen).padding(8.dp))
        }
    }
}

@Composable
private fun PlayerButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Int = 44) {
    // At least 48dp to touch, whatever the drawn size.
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(size.coerceAtLeast(48).dp).pressSwell().clickable(interactionSource = null, indication = null, onClick = onClick).semantics { contentDescription = description }, Alignment.Center) {
        Box(Modifier.size(size.dp).background(Color.Black.copy(alpha = .4f), CircleShape), Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * .55f).dp)) }
    }
}

/** Under the video in portrait: what it is, the other episodes, and similar titles. */
@Composable
private fun PlayerInfo(item: JfItem, nav: Navigator, onSwitch: (String) -> Unit, stopPlayback: () -> Unit) {
    val graph = LocalContext.current.graph
    val series = rememberLoad(item.seriesId) { item.seriesId?.let { graph.api.item(it) } ?: ApiResult.Ok(null) }
    val seasons = rememberLoad(item.seriesId) { item.seriesId?.let { graph.api.seasons(it) } ?: ApiResult.Ok(emptyList()) }
    var chosenSeason by remember(item.id) { mutableStateOf<String?>(null) }
    val seasonId = chosenSeason ?: item.seasonId
    val episodes = rememberLoad(listOf(item.seriesId, seasonId)) { if (item.seriesId != null) graph.api.episodes(item.seriesId, seasonId) else ApiResult.Ok(emptyList()) }
    val similar = rememberLoad(item.seriesId ?: item.id) { graph.api.similar(item.seriesId ?: item.id) }
    val similarById = (similar.state as? Load.Done)?.value.orEmpty().associateBy { it.id }
    val seriesItem = (series.state as? Load.Done)?.value
    val seasonList = (seasons.state as? Load.Done)?.value.orEmpty()

    // A poster tapped here: a movie plays in place; a series (or anything else) opens its page, so playback stops first.
    val infoNav = remember(item.id, similarById) {
        object : Navigator {
            override fun openItem(id: String) { if (similarById[id]?.type == "Movie") onSwitch(id) else { stopPlayback(); nav.openItem(id) } }
            override fun openBrowse(parentId: String?, title: String, types: String?, filter: String?) = nav.openBrowse(parentId, title, types, filter)
            override fun play(id: String) = onSwitch(id)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.seriesName ?: item.name, style = IosType.title2.copy(fontWeight = FontWeight.Bold))
                if (item.isEpisode) Text(listOfNotNull(item.episodeLabel, item.name).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                val facts = listOfNotNull((seriesItem?.year ?: item.year)?.toString(), (seriesItem?.officialRating ?: item.officialRating), item.runtimeMinutes?.let { formatMinutes(it) },
                    (seriesItem?.communityRating ?: item.communityRating)?.let { "★ %.1f".format(it) }, (seriesItem?.childCount ?: seasonList.size.takeIf { it > 0 })?.let { "$it season" + if (it == 1) "" else "s" })
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val genres = (seriesItem?.genres ?: item.genres)
                if (genres.isNotEmpty()) Text(genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.overview?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        if (item.isEpisode) {
            if (seasonList.size > 1) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasonList, key = { it.id }) { s -> com.everettpenne.glass.GlassChip(s.id == seasonId, { chosenSeason = s.id }) { Text(s.name) } }
                }
            }
            (episodes.state as? Load.Done)?.value?.let { eps -> items(eps, key = { it.id }) { ep -> EpisodeRow(ep, { if (ep.id != item.id) onSwitch(ep.id) }, current = ep.id == item.id) } }
        }
        (similar.state as? Load.Done)?.value?.takeIf { it.isNotEmpty() }?.let { more -> item { ShelfRow("More Like This", more, infoNav) } }
    }
}

/** Lists the audio and subtitle tracks the stream offers (with their formats) and applies the pick. Subtitles default to off. */
@OptIn(UnstableApi::class)
@Composable
private fun TrackSheet(player: Player, onDismiss: () -> Unit) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onTracksChanged(t: Tracks) { tracks = t } }
        player.addListener(l); onDispose { player.removeListener(l) }
    }
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported(false) }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || text.none { it.isSelected }

    fun label(g: Tracks.Group, n: Int): String {
        val f = g.getTrackFormat(0)
        val codec = f.sampleMimeType?.substringAfter('/')?.substringAfter("x-")?.uppercase()
        return listOfNotNull(f.label ?: f.language?.let { java.util.Locale(it).displayLanguage }, codec).joinToString(" · ").ifEmpty { "Track ${n + 1}" }
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
