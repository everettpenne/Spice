package com.everettpenne.spice.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.everettpenne.spice.data.api.MediaUrls
import com.everettpenne.spice.data.model.JfItem
import java.util.UUID

/**
 * Turns a list of songs into a Media3 queue and hands it to the service. Media3 then advances from one song to the next on its own, and the
 * existing [PlaybackReporter] reports each song's start, progress and finish (so "played" works), including while the app is in the background.
 * One play session id ties every song of a queue together, as a single listening session on the server.
 */
object AudioQueue {
    /** Replaces whatever the service is playing with [tracks], starting at [startIndex]. [shuffle] plays them in a random order instead. */
    fun play(controller: MediaController, tracks: List<JfItem>, urls: MediaUrls, startIndex: Int = 0, shuffle: Boolean = false) {
        val ordered = if (shuffle) tracks.shuffled() else tracks
        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val items = ordered.mapNotNull { build(it, urls, sessionId) }
        if (items.isEmpty()) return
        controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        controller.prepare()
        controller.playWhenReady = true
    }

    private fun build(track: JfItem, urls: MediaUrls, sessionId: String): MediaItem? {
        val sourceId = track.mediaSources.firstOrNull()?.id
        val url = urls.audioStream(track.id, sourceId) ?: return null
        val metadata = MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.musicArtist)
            .setAlbumTitle(track.album)
            .setExtras(PlaybackMeta.extras(track.id, sourceId ?: "", "DirectStream", sessionId, itemType = "Audio"))
            .apply { urls.poster(track, 600)?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        return MediaItem.Builder().setMediaId(track.id).setUri(url).setMediaMetadata(metadata)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(url)).build()).build()
    }
}
