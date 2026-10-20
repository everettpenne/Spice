package com.everettpenne.spice.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.spice.data.DownloadStatus
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.theme.Spice

/** Download for one movie or episode: an arrow to start, a ring while it comes, a tick when it is on the phone (tap again to delete it). */
@Composable
fun DownloadButton(item: JfItem) {
    val graph = LocalContext.current.graph
    val info = graph.downloads.state.collectAsState().value[item.id]
    val saved by graph.downloads.saved.collectAsState()
    val asked = saved.any { it.item.id == item.id }
    when {
        asked && info?.status == DownloadStatus.DONE -> GlassIconButton(Icons.Rounded.DownloadDone, "Downloaded. Delete the download", { graph.downloads.remove(item.id) }, tint = Spice.accent, size = 48)
        asked && info?.status != DownloadStatus.FAILED -> GlassIconButton(Icons.Rounded.Close, "Cancel the download" + (info?.fraction?.let { ", ${(it * 100).toInt()}%" } ?: ""), { graph.downloads.remove(item.id) }, size = 48)
        else -> GlassIconButton(if (info?.status == DownloadStatus.FAILED) Icons.Rounded.ErrorOutline else Icons.Rounded.Download,
            if (info?.status == DownloadStatus.FAILED) "Download failed. Try again" else "Download", { graph.download(item) }, size = 48)
    }
}

/** Downloads every item in [items] that is not already saved (a season's episodes, an album's songs). Says when they all are. */
@Composable
fun DownloadAllButton(items: List<JfItem>) {
    val graph = LocalContext.current.graph
    val saved by graph.downloads.saved.collectAsState()
    val savedIds = remember(saved) { saved.map { it.item.id }.toSet() }
    val missing = items.filter { it.id !in savedIds }
    if (missing.isEmpty()) GlassIconButton(Icons.Rounded.DownloadDone, "All downloaded", {}, tint = Spice.accent, size = 48)
    else GlassIconButton(Icons.Rounded.Download, if (missing.size == items.size) "Download all ${items.size}" else "Download the other ${missing.size}", { missing.forEach { graph.download(it) } }, size = 48)
}

/** A small mark beside a row: a ring while the item is downloading, a tick once it is on the phone, nothing otherwise. */
@Composable
fun DownloadMark(itemId: String) {
    val graph = LocalContext.current.graph
    val info = graph.downloads.state.collectAsState().value[itemId] ?: return
    when (info.status) {
        DownloadStatus.DONE -> Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = Spice.accent, modifier = Modifier.size(16.dp))
        DownloadStatus.ACTIVE, DownloadStatus.PAUSED -> CircularProgressIndicator(progress = { info.fraction ?: 0f }, modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Spice.accent)
        DownloadStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, "Download failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
    }
}
