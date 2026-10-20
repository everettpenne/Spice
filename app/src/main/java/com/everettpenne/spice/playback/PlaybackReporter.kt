// Media3 marks parts of its API "unstable" (they may change between versions); these are used on purpose and pinned by the version in the build.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.everettpenne.spice.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.everettpenne.spice.data.api.JellyfinApi
import com.everettpenne.spice.data.model.PlaybackReport
import com.everettpenne.spice.data.model.TICKS_PER_SECOND
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tells the Jellyfin server what is playing and how far through it is: started, progress every ten seconds and on pause/resume, stopped (with
 * the position, so "Continue Watching" resumes there), and watched when it ends. It follows the player rather than the screen, so it keeps
 * working with the app in the background, and it reports a replaced or cleared item as stopped at the position it had reached.
 */
class PlaybackReporter(private val api: JellyfinApi, private val player: Player) : Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var startedFor: String? = null      // the media id a start report has been sent for
    private var lastPositionMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            lastPositionMs = player.currentPosition
            if (startedFor != null && player.isPlaying) send(Kind.PROGRESS, player.currentMediaItem, lastPositionMs, paused = false)
            handler.postDelayed(this, 10_000)
        }
    }
    init { handler.postDelayed(tick, 10_000) }

    private enum class Kind { START, PROGRESS, STOPPED }

    private fun send(kind: Kind, item: MediaItem?, positionMs: Long, paused: Boolean) {
        val extras = item?.mediaMetadata?.extras ?: return
        val report = PlaybackReport(
            itemId = extras.getString(PlaybackMeta.ITEM_ID) ?: return, mediaSourceId = extras.getString(PlaybackMeta.SOURCE_ID),
            positionTicks = positionMs.coerceAtLeast(0) * (TICKS_PER_SECOND / 1000), playSessionId = extras.getString(PlaybackMeta.PLAY_SESSION),
            playMethod = extras.getString(PlaybackMeta.METHOD) ?: "DirectStream", isPaused = paused)
        scope.launch { when (kind) { Kind.START -> api.reportStart(report); Kind.PROGRESS -> api.reportProgress(report); Kind.STOPPED -> api.reportStopped(report) } }
    }

    override fun onPlaybackStateChanged(state: Int) {
        val item = player.currentMediaItem ?: return
        if (state == Player.STATE_READY && startedFor != item.mediaId) { startedFor = item.mediaId; send(Kind.START, item, player.currentPosition, paused = !player.isPlaying) }
        if (state == Player.STATE_ENDED && startedFor == item.mediaId) {
            startedFor = null
            send(Kind.STOPPED, item, player.duration.coerceAtLeast(0), paused = true)
            PlaybackMeta.itemId(item)?.let { id -> scope.launch { api.setPlayed(id, true) } }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        lastPositionMs = player.currentPosition
        if (startedFor != null) send(Kind.PROGRESS, player.currentMediaItem, lastPositionMs, paused = !isPlaying)
    }

    /** An item being replaced or cleared: report the old one stopped where it was. A song that finished and advanced in the queue counts as played. */
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val old = oldPosition.mediaItem ?: return
        if (startedFor == old.mediaId && (reason == Player.DISCONTINUITY_REASON_REMOVE || reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION)) {
            startedFor = null
            send(Kind.STOPPED, old, oldPosition.positionMs, paused = true)
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) PlaybackMeta.itemId(old)?.let { id -> scope.launch { api.setPlayed(id, true) } }
        }
    }

    /** The service is going away mid-playback. */
    fun finish() {
        handler.removeCallbacks(tick)
        val item = player.currentMediaItem
        if (startedFor != null && item != null) send(Kind.STOPPED, item, player.currentPosition, paused = true)
        startedFor = null
    }
}
