package com.everettpenne.spice.playback

import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the playback service is doing right now, published as a flow by [PlaybackService]. The mini-player (in the shell, above the tab bar)
 * reads this instead of holding its own MediaController, so showing the tab bar does not keep the playback service alive when nothing plays.
 */
object PlaybackStatus {
    data class Now(val item: MediaItem?, val isPlaying: Boolean, val itemType: String?)

    private val _now = MutableStateFlow(Now(null, false, null))
    val now: StateFlow<Now> = _now.asStateFlow()

    fun update(item: MediaItem?, isPlaying: Boolean) { _now.value = Now(item, isPlaying, PlaybackMeta.itemType(item)) }
    fun clear() { _now.value = Now(null, false, null) }
}
