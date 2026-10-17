package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.components.*
import com.everettpenne.glass.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Everything the home page shows, fetched together. A shelf that fails to load is left out rather than failing the page. */
class HomeData(val resume: List<JfItem>, val nextUp: List<JfItem>, val latest: List<Pair<JfItem, List<JfItem>>>)

@Composable
fun HomeScreen(nav: Navigator, onSettings: () -> Unit) {
    val graph = LocalContext.current.graph
    val session by graph.sessions.session.collectAsState()
    val load = rememberLoad(session?.userId) {
        coroutineScope {
            val libs = async { graph.api.libraries() }
            val resume = async { graph.api.resume() }
            val next = async { graph.api.nextUp() }
            val libraries = libs.await()
            if (libraries is ApiResult.Failed) return@coroutineScope libraries
            val latest = (libraries as ApiResult.Ok).value
                .filter { it.collectionType in setOf("movies", "tvshows", "homevideos", "music", null) && it.collectionType != "music" }
                .map { lib -> lib to async { graph.api.latest(lib.id).valueOrNull.orEmpty() } }
                .map { (lib, items) -> lib to items.await() }
            ApiResult.Ok(HomeData(resume.await().valueOrNull.orEmpty(), next.await().valueOrNull.orEmpty(), latest))
        }
    }
    GlassScreen(bars = {
        GlassTopBar("Home", Modifier.align(Alignment.TopCenter)) {
            GlassIconButton(Icons.Rounded.Settings, "Settings", onSettings, size = 48)
        }
    }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topContentPadding() + 8.dp, bottom = bottomContentPadding()), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            when (val s = load.state) {
                Load.Loading -> item { LoadingRow() }
                is Load.Error -> item { ErrorState(s.failure, load.reload) }
                is Load.Done -> {
                    val d = s.value
                    item { ShelfRow("Continue Watching", d.resume, nav, wide = true) }
                    item { ShelfRow("Next Up", d.nextUp, nav, wide = true) }
                    d.latest.forEach { (lib, items) -> item(key = lib.id) {
                        ShelfRow("Latest in ${lib.name}", items, nav, onMore = { nav.openBrowse(lib.id, lib.name, types = typesFor(lib.collectionType)) })
                    } }
                    if (d.resume.isEmpty() && d.nextUp.isEmpty() && d.latest.all { it.second.isEmpty() })
                        item { EmptyState(Icons.Rounded.VideoLibrary, "Nothing here yet", "Add media to a library on your server and it will show up.") }
                }
            }
        }
    }
}

/** What to list in a library: its movies, series or albums, not every file and folder. */
fun typesFor(collectionType: String?): String? = when (collectionType) { "movies" -> "Movie"; "tvshows" -> "Series"; "boxsets" -> "BoxSet"; "music" -> "MusicAlbum"; else -> null }
