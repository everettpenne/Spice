package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
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

/** Search movies, series and episodes. It searches as you type, after a short pause. The field sits at the bottom, above the keyboard or the tab bar. */
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
        when (val r = graph.api.items(types = "Movie,Series,Episode,BoxSet", search = query.trim(), limit = 48, sortBy = "SortName")) {
            is ApiResult.Ok -> results = r.value.items
            is ApiResult.Failed -> failure = r.reason
        }
        loading = false
    }

    GlassScreen(bars = {
        GlassTopBar("Search", Modifier.align(Alignment.TopCenter))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().padding(horizontal = 16.dp)
            .padding(bottom = if (imeVisible) 8.dp else 92.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
            GlassTextField(query, { query = it }, "Movies, shows, episodes", leading = Icons.Rounded.Search, imeAction = ImeAction.Search)
        }
    }) {
        LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topContentPadding(), bottom = 220.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow() }
            failure?.let { f -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(f) { retry++ } } }
            items(results, key = { it.id }) { PosterCard(it, nav) }
            if (!loading && failure == null && query.isNotBlank() && results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.Search, "No results", "Try a different search.") }
        }
    }
}
