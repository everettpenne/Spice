package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.api.Failure
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.components.*
import com.everettpenne.glass.*

/** The Library tab: favourites, then each library as a wide tile with its own artwork. */
@Composable
fun LibraryScreen(nav: Navigator) {
    val graph = LocalContext.current.graph
    val session by graph.sessions.session.collectAsState()
    val load = rememberLoad(session?.userId) { graph.api.libraries() }
    GlassScreen(bars = { GlassTopBar("Library", Modifier.align(Alignment.TopCenter)) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topContentPadding() + 8.dp, bottom = bottomContentPadding(), start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { LibraryTile("Favorites", Icons.Rounded.Favorite, null) { nav.openBrowse(null, "Favorites", filter = "IsFavorite") } }
            when (val s = load.state) {
                Load.Loading -> item { LoadingRow() }
                is Load.Error -> item { ErrorState(s.failure, load.reload) }
                is Load.Done -> items(s.value, key = { it.id }) { lib ->
                    LibraryTile(lib.name, iconFor(lib.collectionType), graph.urls.wide(lib, 700)) { nav.openBrowse(lib.id, lib.name, types = typesFor(lib.collectionType)) }
                }
            }
        }
    }
}

private fun iconFor(collectionType: String?) = when (collectionType) {
    "movies" -> Icons.Rounded.Movie; "tvshows" -> Icons.Rounded.Tv; "music" -> Icons.Rounded.MusicNote; "homevideos" -> Icons.Rounded.Videocam
    "boxsets" -> Icons.Rounded.Collections; else -> Icons.Rounded.Folder
}

/** A library as a plain row: icon, name, chevron, as in a settings list. */
@Composable
private fun LibraryTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, @Suppress("UNUSED_PARAMETER") imageUrl: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = com.everettpenne.spice.ui.theme.Spice.accent, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class Sort(val label: String, val by: String, val order: String) {
    Name("Name", "SortName", "Ascending"), Added("Recently Added", "DateCreated", "Descending"),
    Year("Year", "ProductionYear,SortName", "Descending"), Rating("Rating", "CommunityRating,SortName", "Descending"),
}

/** A library (or the favourites) as a grid of posters, sortable, loading more as you scroll. */
@Composable
fun BrowseScreen(parentId: String?, title: String, types: String?, filter: String?, nav: Navigator, onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    var sort by remember { mutableStateOf(Sort.Name) }
    val items = remember { mutableStateListOf<JfItem>() }
    var total by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var failure by remember { mutableStateOf<Failure?>(null) }
    var loading by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - 12 } }

    // Loads a page whenever the scroll gets near the end of what is loaded, until the server has no more. `loading` is deliberately not a key of
    // this effect: it is set from inside it, and a changed key would cancel the very request that set it (the screen then sat on its spinner forever).
    LaunchedEffect(sort, generation) {
        items.clear(); total = Int.MAX_VALUE; failure = null
        while (items.size < total) {
            snapshotFlow { nearEnd }.first { it }
            loading = true
            when (val r = graph.api.items(parentId, types, sort.by, sort.order, start = items.size, limit = 60, filters = filter, recursive = parentId != null || filter != null)) {
                is ApiResult.Ok -> {
                    total = if (r.value.items.isEmpty()) items.size else r.value.total
                    items.addAll(r.value.items.filter { n -> items.none { it.id == n.id } })
                }
                is ApiResult.Failed -> { failure = r.reason; loading = false; return@LaunchedEffect }
            }
            loading = false
        }
    }

    GlassScreen(bars = { GlassCenteredTopBar(title, onBack, Modifier.align(Alignment.TopCenter)) }) {
        LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize(), state = gridState,
            contentPadding = PaddingValues(top = topContentPadding() + 8.dp, bottom = bottomContentPadding(), start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(Sort.entries) { s -> GlassChip(sort == s, { sort = s }) { Text(s.label) } } }
            }
            itemsIndexed(items, key = { _, it -> it.id }) { _, item -> PosterCard(item, nav) }
            failure?.let { f -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(f) { failure = null; generation++ } } }
            if (loading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow() }
            if (!loading && failure == null && items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.Movie, "Nothing here", "This library is empty.") }
        }
    }
}
