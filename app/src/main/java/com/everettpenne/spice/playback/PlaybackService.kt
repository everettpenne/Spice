package com.everettpenne.spice.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.everettpenne.spice.MainActivity
import com.everettpenne.spice.graph
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The keys that tie a playing [MediaItem] back to Jellyfin: the item, its media source, how it is being played, and the play session. */
object PlaybackMeta {
    const val ITEM_ID = "spice.itemId"
    const val SOURCE_ID = "spice.sourceId"
    const val METHOD = "spice.method"          // "DirectStream" or "Transcode"
    const val PLAY_SESSION = "spice.playSession"
    const val ITEM_TYPE = "spice.itemType"     // the Jellyfin item type ("Audio" for songs)

    fun itemId(item: MediaItem?): String? = item?.mediaMetadata?.extras?.getString(ITEM_ID)
    fun method(item: MediaItem?): String? = item?.mediaMetadata?.extras?.getString(METHOD)
    fun itemType(item: MediaItem?): String? = item?.mediaMetadata?.extras?.getString(ITEM_TYPE)

    fun extras(itemId: String, sourceId: String, method: String, playSession: String, itemType: String = "") = Bundle().apply {
        putString(ITEM_ID, itemId); putString(SOURCE_ID, sourceId); putString(METHOD, method); putString(PLAY_SESSION, playSession)
        if (itemType.isNotEmpty()) putString(ITEM_TYPE, itemType)
    }
}

/**
 * Owns the player, so playback outlives the screen: switch to another app, or lock the phone, and the sound carries on, with a media notification
 * to pause or skip. The screen talks to it through a MediaController. Leaving the player screen stops playback (the screen asks for that);
 * swiping the app away from recents does too. Progress is reported to the Jellyfin server from here ([PlaybackReporter]), so it is still
 * reported while the app is in the background.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var reporter: PlaybackReporter? = null

    override fun onCreate() {
        super.onCreate()
        val graph = applicationContext.graph
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(graph.http)))
            // Movie audio, and let the system manage focus: a phone call or another player pauses this one, and it pauses when headphones are unplugged.
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .build()
        reporter = PlaybackReporter(graph.api, player).also { player.addListener(it) }
        // Publish what is playing for the shell's mini-player, without that shell holding its own controller (and so without keeping
        // this service alive when nothing is playing).
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { PlaybackStatus.update(mediaItem, player.isPlaying) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { PlaybackStatus.update(player.currentMediaItem, isPlaying) }
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open).setCallback(object : MediaSession.Callback {
            /** Controllers send items without their address (it does not cross the session boundary); it travels in the request metadata. */
            override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
                Futures.immediateFuture(mediaItems.map { item -> item.requestMetadata.mediaUri?.let { item.buildUpon().setUri(it).build() } ?: item }.toMutableList())
        }).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away ends playback, as it does in other players. */
    override fun onTaskRemoved(rootIntent: Intent?) { pauseAllPlayersAndStopSelf() }

    override fun onDestroy() {
        PlaybackStatus.clear()
        reporter?.finish()
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
