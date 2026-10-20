package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.components.*
import com.everettpenne.glass.*
import com.everettpenne.glass.IosType
import com.everettpenne.spice.ui.theme.Spice
import kotlinx.coroutines.launch

/** A movie, series, season or episode: artwork, the facts, play and the watched/favourite switches, then episodes, cast and similar titles. */
@Composable
fun DetailScreen(id: String, nav: Navigator, onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val load = rememberLoad(id) { graph.api.item(id) }
    GlassScreen(bars = { GlassCenteredTopBar("", onBack, Modifier.align(Alignment.TopCenter)) }) {
        when (val s = load.state) {
            Load.Loading -> LoadingRow()
            is Load.Error -> ErrorState(s.failure, load.reload)
            is Load.Done -> DetailBody(s.value, nav, onChanged = load.reload, scope = scope)
        }
    }
}

@Composable
private fun DetailBody(item: JfItem, nav: Navigator, onChanged: () -> Unit, scope: kotlinx.coroutines.CoroutineScope) {
    val graph = LocalContext.current.graph
    var favorite by remember(item.id, item.userData.isFavorite) { mutableStateOf(item.userData.isFavorite) }
    var played by remember(item.id, item.userData.played) { mutableStateOf(item.userData.played) }
    val seriesId = if (item.isSeries) item.id else if (item.isSeason || item.isEpisode) item.seriesId else null
    val seasons = rememberLoad(seriesId) { if (item.isSeries) graph.api.seasons(item.id) else ApiResult.Ok(emptyList()) }
    var season by remember(item.id) { mutableStateOf<JfItem?>(null) }
    val seasonList = (seasons.state as? Load.Done)?.value.orEmpty()
    val chosenSeasonId = season?.id ?: item.seasonId.takeIf { item.isEpisode } ?: if (item.isSeason) item.id else seasonList.firstOrNull { it.userData.unplayedItemCount != 0 && (it.index ?: 1) > 0 }?.id ?: seasonList.firstOrNull()?.id
    val episodes = rememberLoad(listOf(seriesId, chosenSeasonId, item.id)) {
        if (seriesId != null && (item.isSeason || item.isSeries && chosenSeasonId != null)) graph.api.episodes(seriesId, chosenSeasonId) else ApiResult.Ok(emptyList())
    }
    val episodeList = (episodes.state as? Load.Done)?.value.orEmpty()
    val similar = rememberLoad(item.id) { if (item.isSeries || item.type == "Movie") graph.api.similar(item.id) else ApiResult.Ok(emptyList()) }

    // What Play does: a movie or episode plays itself; a series plays its first unwatched episode (or the first).
    val playTarget: JfItem? = when { item.isPlayable -> item; else -> episodeList.firstOrNull { !it.userData.played } ?: episodeList.firstOrNull() }
    val resumeTicks = playTarget?.userData?.positionTicks ?: 0

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentBottomPadding()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                ItemImage(graph.urls.backdrop(item), Modifier.fillMaxSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.seriesName?.takeIf { item.isEpisode || item.isSeason } ?: item.name, style = IosType.title1.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                if (item.isEpisode) Text(listOfNotNull(item.episodeLabel, item.name).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                val facts = listOfNotNull(item.year?.toString(), item.officialRating, item.runtimeMinutes?.let { formatMinutes(it) },
                    item.communityRating?.let { "★ %.1f".format(it) }, item.childCount?.takeIf { item.isSeries }?.let { "$it seasons" })
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (playTarget != null) GlassButton({ nav.play(playTarget.id) }, Modifier.weight(1f), prominent = true) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Text(if (resumeTicks > 0) "Resume" else "Play" + (if (!item.isPlayable) playTarget.episodeLabel?.let { " $it" }.orEmpty() else ""))
                    } else Spacer(Modifier.weight(1f))
                    if (item.isPlayable || item.isSeries) {
                        GlassIconButton(if (played) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline, if (played) "Mark unwatched" else "Mark watched",
                            { played = !played; scope.launch { graph.api.setPlayed(item.id, played) } }, tint = if (played) Spice.accent else null, size = 48)
                        GlassIconButton(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (favorite) "Remove favorite" else "Add favorite",
                            { favorite = !favorite; scope.launch { graph.api.setFavorite(item.id, favorite) } }, tint = if (favorite) Spice.red else null, size = 48)
                    }
                    if (item.isPlayable) DownloadButton(item)
                }
                item.overview?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (item.genres.isNotEmpty()) Text(item.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (item.isSeries && seasonList.size > 1) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasonList, key = { it.id }) { s -> GlassChip(s.id == chosenSeasonId, { season = s }) { Text(s.name) } }
            }
        }
        if ((item.isSeries || item.isSeason) && episodeList.isNotEmpty()) item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(seasonList.firstOrNull { it.id == chosenSeasonId }?.name ?: "Episodes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                DownloadAllButton(episodeList)
            }
        }
        if (item.isSeries || item.isSeason) {
            when (val e = episodes.state) {
                Load.Loading -> item { LoadingRow() }
                is Load.Error -> item { ErrorState(e.failure, episodes.reload) }
                is Load.Done -> items(e.value, key = { it.id }) { ep -> EpisodeRow(ep, { nav.play(ep.id) }) }
            }
        }
        val cast = item.people.filter { it.type == "Actor" }.take(8).map { it.name }
        if (cast.isNotEmpty()) item {
            Text("With " + cast.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
        (similar.state as? Load.Done)?.value?.takeIf { it.isNotEmpty() }?.let { more -> item { ShelfRow("More Like This", more, nav) } }
    }
}

/** One episode in a list: still, number and title, runtime, a line of the story. [current] marks the one playing now. */
@Composable
fun EpisodeRow(ep: JfItem, onClick: () -> Unit, current: Boolean = false) {
    val graph = LocalContext.current.graph
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            ItemImage(graph.urls.wide(ep, 400), Modifier.fillMaxSize())
            ProgressStrip(ep.progress)
            if (ep.userData.played) Icon(Icons.Rounded.CheckCircle, "Watched", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${ep.index?.let { "$it. " }.orEmpty()}${ep.name}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (current) Spice.accent else MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ep.runtimeMinutes?.let { Text(formatMinutes(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                DownloadMark(ep.id)
            }
            ep.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

fun formatMinutes(total: Int): String = if (total >= 60) "${total / 60}h ${total % 60}m" else "${total}m"
