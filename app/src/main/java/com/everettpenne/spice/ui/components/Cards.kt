package com.everettpenne.spice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.graph
import com.everettpenne.glass.IosType
import com.everettpenne.spice.ui.theme.Spice

/** A picture from the server, or a quiet placeholder while it loads or when the item has none. [placeholderIcon] is the glyph behind it. */
@Composable
fun ItemImage(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null, placeholder: Boolean = true, placeholderIcon: ImageVector = Icons.Rounded.Movie) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (placeholder) Icon(placeholderIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f), modifier = Modifier.size(32.dp))
        if (url != null) AsyncImage(url, contentDescription, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

/** The thin bar along the bottom of a picture showing how far through it you are. */
@Composable
fun BoxScope.ProgressStrip(progress: Float?) {
    if (progress == null) return
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .35f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Spice.accent))
    }
}

/** A small badge: how many episodes are unwatched, or a tick-less "new" count. */
@Composable
private fun BoxScope.CountBadge(count: Int?) {
    if (count == null || count <= 0) return
    Text("$count", style = IosType.caption1.copy(fontWeight = FontWeight.Bold), color = Color.White,
        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Spice.accent, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp))
}

/** A tall poster with the title beneath, as in a library grid. */
@Composable
fun PosterCard(item: JfItem, nav: Navigator, modifier: Modifier = Modifier) {
    val urls = LocalContext.current.graph.urls
    Column(modifier.clickable {
        when {
            item.isMusicAlbum -> nav.openAlbum(item.id)
            item.isMusicArtist -> nav.openArtist(item.id)
            item.isEpisode -> nav.openItem(item.seriesId ?: item.id)
            else -> nav.openItem(item.id)
        }
    }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
            ItemImage(urls.poster(item, 360), Modifier.fillMaxSize(), item.name)
            ProgressStrip(item.progress)
            CountBadge(if (item.isSeries || item.isSeason) item.userData.unplayedItemCount else null)
        }
        Text(item.seriesName ?: item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = item.episodeLabel?.let { "$it · ${item.name}" } ?: item.year?.toString()
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A wide card for things you can resume: the still and a progress strip, nothing more. Tapping plays. */
@Composable
fun WideCard(item: JfItem, nav: Navigator, width: Dp = 260.dp) {
    val urls = LocalContext.current.graph.urls
    Column(Modifier.width(width).clickable { if (item.isPlayable) nav.play(item.id) else nav.openItem(item.id) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))) {
            ItemImage(urls.wide(item, 640), Modifier.fillMaxSize(), item.name)
            ProgressStrip(item.progress)
        }
        Text(item.seriesName ?: item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = if (item.isEpisode) listOfNotNull(item.episodeLabel, item.name).joinToString(" · ") else item.year?.toString()
        if (!sub.isNullOrEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A titled, sideways-scrolling row of cards. [onMore] (when given) makes the title a "See all" link. */
@Composable
fun ShelfRow(title: String, items: List<JfItem>, nav: Navigator, wide: Boolean = false, onMore: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().then(if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onMore != null) Text("See all", style = MaterialTheme.typography.subheadlineAccent(), color = Spice.accent)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { item -> if (wide) WideCard(item, nav) else PosterCard(item, nav, Modifier.width(126.dp)) }
        }
    }
}

private fun androidx.compose.material3.Typography.subheadlineAccent() = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold)
