package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.everettpenne.glass.*
import com.everettpenne.spice.data.DownloadStatus
import com.everettpenne.spice.data.Downloads
import com.everettpenne.spice.data.SavedItem
import com.everettpenne.spice.graph
import com.everettpenne.spice.playback.AudioQueue
import com.everettpenne.spice.ui.components.*
import com.everettpenne.spice.ui.theme.Spice

/**
 * What is saved on the phone, newest first, with how much room it takes: films and episodes play from here with no server; songs play as a queue
 * of everything downloaded. Each row can be deleted; a failed one can be tried again.
 */
@Composable
fun DownloadsScreen(nav: Navigator, onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val saved by graph.downloads.saved.collectAsState()
    val state by graph.downloads.state.collectAsState()
    val controller = rememberMediaController()
    var confirmAll by remember { mutableStateOf(false) }
    val songs = saved.filter { it.item.isAudio && state[it.item.id]?.status == DownloadStatus.DONE }
    GlassScreen(bars = {
        GlassCenteredTopBar("Downloads", onBack, Modifier.align(Alignment.TopCenter))
    }) { contentPadding ->
        if (saved.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Rounded.DownloadForOffline, "No downloads", "Download a film, an episode, a season or an album to watch or listen without a connection.")
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${saved.size} ${if (saved.size == 1) "item" else "items"}  ·  ${formatBytes(graph.downloads.totalBytes(state))}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (songs.isNotEmpty()) GlassButton({ controller?.let { AudioQueue.play(it, songs.map { s -> s.item }, graph.urls, local = graph.downloads::fileFor) } }) {
                        Icon(Icons.Rounded.PlayArrow, null); Text("Play songs")
                    }
                    GlassIconButton(Icons.Rounded.DeleteSweep, "Delete all downloads", { confirmAll = true }, size = 44)
                }
            }
            items(saved, key = { it.item.id }) { s -> SavedRow(s, onPlay = { nav.play(s.item.id) }) }
        }
    }
    if (confirmAll) GlassAlert("Delete all downloads?", "They are removed from this phone. Everything stays on your server.", confirmLabel = "Delete All", destructive = true,
        onConfirm = { confirmAll = false; graph.downloads.removeAll() }, onDismiss = { confirmAll = false })
}

@Composable
private fun SavedRow(s: SavedItem, onPlay: () -> Unit) {
    val graph = LocalContext.current.graph
    val info = graph.downloads.state.collectAsState().value[s.item.id]
    val done = info?.status == DownloadStatus.DONE
    Row(Modifier.fillMaxWidth().clickable(enabled = done && !s.item.isAudio, onClick = onPlay).padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val poster = graph.downloads.posterFor(s.item.id)
        Box(Modifier.width(if (s.item.isAudio) 56.dp else 64.dp).aspectRatio(if (s.item.isAudio) 1f else 2f / 3f).clip(RoundedCornerShape(10.dp))) {
            if (poster != null) AsyncImage(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else ItemImage(graph.urls.poster(s.item, 200), Modifier.fillMaxSize(), s.item.name)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(Downloads.title(s.item), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = when (info?.status) {
                DownloadStatus.DONE -> formatBytes(info.bytes)
                DownloadStatus.FAILED -> info.problem ?: "Failed"
                DownloadStatus.PAUSED -> info.problem ?: "Paused"
                else -> info?.fraction?.let { "Downloading  ${(it * 100).toInt()}%  ·  ${formatBytes(info.bytes)} of ${formatBytes(info.total)}" } ?: "Waiting to start"
            }
            Text(line, style = MaterialTheme.typography.bodySmall, color = if (info?.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (info?.status == DownloadStatus.ACTIVE) LinearProgressIndicator(progress = { info.fraction ?: 0f }, Modifier.fillMaxWidth(), color = Spice.accent)
        }
        if (info?.status == DownloadStatus.FAILED) GlassIconButton(Icons.Rounded.Refresh, "Try again", { graph.download(s.item) }, size = 40)
        GlassIconButton(if (done) Icons.Rounded.Delete else Icons.Rounded.Close, if (done) "Delete download" else "Cancel download", { graph.downloads.remove(s.item.id) }, size = 40)
    }
}

/** "1.4 GB", "350 MB", "12 KB". */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    bytes > 0 -> "${(bytes / 1024).coerceAtLeast(1)} KB"
    else -> "0 KB"
}
