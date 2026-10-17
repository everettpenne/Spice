package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.*
import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.model.JfItem
import com.everettpenne.spice.graph
import com.everettpenne.spice.playback.AudioQueue
import com.everettpenne.spice.playback.PlaybackMeta
import com.everettpenne.spice.ui.components.*
import com.everettpenne.spice.ui.theme.Spice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One music library on the landing: its artists and its newest albums, fetched together. */
private class MusicLibrary(val library: JfItem, val artists: List<JfItem>, val albums: List<JfItem>)

/**
 * The Music tab: the artists of the music libraries, then each library's newest albums. The same shelves-and-artwork shape as Home and Library,
 * so the new tab sits naturally beside them.
 */
@Composable
fun MusicScreen(nav: Navigator) {
    val graph = LocalContext.current.graph
    val session by graph.sessions.session.collectAsState()
    val load = rememberLoad(session?.userId) {
        coroutineScope {
            val libs = graph.api.musicLibraries()
            if (libs is ApiResult.Failed) return@coroutineScope libs
            val libraries = (libs as ApiResult.Ok).value.map { lib ->
                val artists = async { graph.api.artists(lib.id).valueOrNull.orEmpty() }
                val albums = async { graph.api.latestAlbums(lib.id).valueOrNull.orEmpty() }
                Triple(lib, artists, albums)
            }.map { (lib, a, al) -> MusicLibrary(lib, a.await(), al.await()) }
            ApiResult.Ok(libraries)
        }
    }
    GlassScreen(bars = { GlassTopBar("Music", Modifier.align(Alignment.TopCenter)) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topContentPadding() + 8.dp, bottom = bottomContentPadding()),
            verticalArrangement = Arrangement.spacedBy(26.dp)) {
            when (val s = load.state) {
                Load.Loading -> item { LoadingRow() }
                is Load.Error -> item { ErrorState(s.failure, load.reload) }
                is Load.Done -> {
                    val libs = s.value
                    val artists = libs.flatMap { it.artists }.distinctBy { it.id }
                    if (artists.isNotEmpty()) item { MusicShelf("Artists", artists, nav, artists = true) }
                    libs.forEach { lib -> item(key = lib.library.id) { MusicShelf("Latest in ${lib.library.name}", lib.albums, nav) } }
                    if (artists.isEmpty() && libs.all { it.albums.isEmpty() })
                        item { EmptyState(Icons.Rounded.MusicNote, "Nothing here yet", "Add a music library on your server and it will show up.") }
                }
            }
        }
    }
}

/** A titled, sideways-scrolling shelf of music: circular artist portraits or square album covers. */
@Composable
private fun MusicShelf(title: String, items: List<JfItem>, nav: Navigator, artists: Boolean = false) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { item -> if (artists) ArtistCircle(item, nav) else AlbumCard(item, nav, Modifier.width(140.dp)) }
        }
    }
}

/** A square album cover with its title and artist beneath. */
@Composable
fun AlbumCard(item: JfItem, nav: Navigator, modifier: Modifier = Modifier) {
    val urls = LocalContext.current.graph.urls
    Column(modifier.clickable { nav.openAlbum(item.id) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))) {
            ItemImage(urls.poster(item, 360), Modifier.fillMaxSize(), item.name, placeholderIcon = Icons.Rounded.Album)
        }
        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = item.musicArtist ?: item.year?.toString()
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A round artist portrait with the name beneath. */
@Composable
fun ArtistCircle(item: JfItem, nav: Navigator, modifier: Modifier = Modifier) {
    val urls = LocalContext.current.graph.urls
    Column(modifier.width(84.dp).clickable { nav.openArtist(item.id) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape)) {
            ItemImage(urls.poster(item, 200), Modifier.fillMaxSize(), item.name, placeholderIcon = Icons.Rounded.Person)
        }
        Text(item.name, style = IosType.caption1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An artist: their portrait, name, and the albums they appear on. */
@Composable
fun MusicArtistScreen(id: String, nav: Navigator, onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val load = rememberLoad(id) { graph.api.item(id) }
    GlassScreen(bars = { GlassCenteredTopBar("", onBack, Modifier.align(Alignment.TopCenter)) }) {
        when (val s = load.state) {
            Load.Loading -> LoadingRow()
            is Load.Error -> ErrorState(s.failure, load.reload)
            is Load.Done -> ArtistBody(s.value, nav)
        }
    }
}

@Composable
private fun ArtistBody(artist: JfItem, nav: Navigator) {
    val graph = LocalContext.current.graph
    val albums = rememberLoad(artist.id) { graph.api.albumsByArtist(artist.id) }
    LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topContentPadding() + 8.dp, bottom = bottomContentPadding(), start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(160.dp).clip(CircleShape)) {
                    ItemImage(graph.urls.poster(artist, 320), Modifier.fillMaxSize(), artist.name, placeholderIcon = Icons.Rounded.Person)
                }
                Text(artist.name, style = IosType.title1.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (val a = albums.state) {
            Load.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow() }
            is Load.Error -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(a.failure, albums.reload) }
            is Load.Done -> items(a.value, key = { it.id }) { AlbumCard(it, nav) }
        }
    }
}

/** An album: the cover, the facts, Play and Shuffle, and the track list in disc/track order. */
@Composable
fun MusicAlbumScreen(id: String, nav: Navigator, onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val load = rememberLoad(id) { graph.api.item(id) }
    GlassScreen(bars = { GlassCenteredTopBar("", onBack, Modifier.align(Alignment.TopCenter)) }) {
        when (val s = load.state) {
            Load.Loading -> LoadingRow()
            is Load.Error -> ErrorState(s.failure, load.reload)
            is Load.Done -> AlbumBody(s.value)
        }
    }
}

@Composable
private fun AlbumBody(album: JfItem) {
    val graph = LocalContext.current.graph
    val controller = rememberMediaController()
    val now = rememberNowPlaying(controller)
    val currentId = PlaybackMeta.itemId(now.item)
    val tracksLoad = rememberLoad(album.id) { graph.api.albumTracks(album.id) }
    val tracks = (tracksLoad.state as? Load.Done)?.value.orEmpty()

    fun play(start: Int = 0, shuffle: Boolean = false) {
        controller?.let { AudioQueue.play(it, tracks, graph.urls, startIndex = start, shuffle = shuffle) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topContentPadding(), bottom = bottomContentPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(240.dp).clip(RoundedCornerShape(18.dp))) {
                    ItemImage(graph.urls.poster(album, 600), Modifier.fillMaxSize(), album.name, placeholderIcon = Icons.Rounded.Album)
                }
                Text(album.name, style = IosType.title1.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val facts = listOfNotNull(album.musicArtist, album.year?.toString()).joinToString(" · ")
                if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tracks.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton({ play() }, Modifier.weight(1f), prominent = true) { Icon(Icons.Rounded.PlayArrow, null); Text("Play") }
                        GlassButton({ play(shuffle = true) }, Modifier.weight(1f)) { Icon(Icons.Rounded.Shuffle, null); Text("Shuffle") }
                    }
                }
            }
        }
        when (val t = tracksLoad.state) {
            Load.Loading -> item { LoadingRow() }
            is Load.Error -> item { ErrorState(t.failure, tracksLoad.reload) }
            is Load.Done -> items(t.value, key = { it.id }) { track ->
                TrackRow(track, playing = track.id == currentId) { play(start = t.value.indexOfFirst { it.id == track.id }) }
            }
        }
    }
}

/** One track of an album: its number (or an equaliser when it is the one playing), title and duration. */
@Composable
fun TrackRow(track: JfItem, playing: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            if (playing) Icon(Icons.Rounded.GraphicEq, null, tint = Spice.accent, modifier = Modifier.size(20.dp))
            else Text(track.trackLabel ?: "•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(track.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) Spice.accent else MaterialTheme.colorScheme.onSurface)
            track.musicArtist?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        track.runtimeSeconds?.let { Text(formatTrackTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** "3:45" for a track; hours only when a track is that long. */
internal fun formatTrackTime(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
