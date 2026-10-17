package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.api.Failure
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.components.*
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassTopBar
import kotlinx.coroutines.delay

/** Search movies, shows, episodes, albums and artists. It searches as you type, after a short pause, and groups what it finds by kind. */
@Composable
fun SearchScreen(nav: Navigator) {
    val graph = LocalContext.current.graph
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Failure?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

    LaunchedEffect(query, retry) {
        failure = null
        if (query.isBlank()) { results = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        delay(350)
        when (val r = graph.api.items(types = "Movie,Series,Episode,BoxSet,MusicAlbum,MusicArtist", search = query.trim(), limit = 48, sortBy = "SortName")) {
            is ApiResult.Ok -> results = r.value.items
            is ApiResult.Failed -> failure = r.reason
        }
        loading = false
    }

    val groups = remember(results) {
        listOf(
            "Movies" to results.filter { it.type == "Movie" || it.type == "BoxSet" },
            "Shows" to results.filter { it.type == "Series" },
            "Episodes" to results.filter { it.type == "Episode" },
            "Albums" to results.filter { it.type == "MusicAlbum" },
            "Artists" to results.filter { it.type == "MusicArtist" },
        ).filter { it.second.isNotEmpty() }
    }

    GlassScreen(bars = {
        GlassTopBar("Search", Modifier.align(Alignment.TopCenter))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().padding(horizontal = 16.dp)
            .padding(bottom = if (imeVisible) 8.dp else 92.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
            GlassTextField(query, { query = it }, "Movies, shows, music", leading = Icons.Rounded.Search, imeAction = ImeAction.Search)
        }
    }) { contentPadding ->
        LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 220.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            if (loading) item { LoadingRow() }
            failure?.let { f -> item { ErrorState(f) { retry++ } } }
            groups.forEach { (title, group) -> item(key = title) { SearchShelf(title, group, nav) } }
            if (!loading && failure == null && query.isNotBlank() && results.isEmpty()) item { EmptyState(Icons.Rounded.Search, "No results", "Try a different search.") }
        }
    }
}

/** One titled row of a search group, drawn with the card shape that suits the kind: posters for video, squares for albums, circles for artists. */
@Composable
private fun SearchShelf(title: String, items: List<JfItem>, nav: Navigator) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { item ->
                when (item.type) {
                    "MusicAlbum" -> AlbumCard(item, nav, Modifier.width(140.dp))
                    "MusicArtist" -> ArtistCircle(item, nav)
                    else -> PosterCard(item, nav, Modifier.width(126.dp))
                }
            }
        }
    }
}
