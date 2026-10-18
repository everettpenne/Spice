package com.everettpenne.spice.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Picture-in-picture is for video only: a movie, an episode or a music video, and only while it is actually playing.
 * Music ("Audio") keeps backgrounding through the media notification instead, so it never enters PiP.
 */
object PiP {
    /** The Jellyfin item types that play as video. Audio, and everything that is not a video (folders, albums, series...), do not. */
    private val VIDEO_TYPES = setOf("Movie", "Episode", "Video", "MusicVideo")

    /** Whether [type] is a video item type (PiP-eligible) rather than music. */
    fun isVideoType(type: String?): Boolean = type != null && type in VIDEO_TYPES

    /** Whether leaving the app should enter picture-in-picture: a video that is actually playing. */
    fun shouldEnterPip(type: String?, isPlaying: Boolean): Boolean = isPlaying && isVideoType(type)

    private val _active = MutableStateFlow(false)
    /** True while the activity is in the picture-in-picture window: the player hides its normal UI and shows just the video. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun setActive(active: Boolean) { _active.value = active }
}
