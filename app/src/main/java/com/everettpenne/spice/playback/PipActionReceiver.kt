// Media3 marks parts of its API "unstable" (they may change between versions); these are used on purpose and pinned by the version in the build.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.everettpenne.spice.playback

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** The play/pause button of the picture-in-picture window: toggles the playback service without leaving PiP. */
class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val pending = goAsync()
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller != null) { if (controller.isPlaying) controller.pause() else controller.play(); controller.release() }
            MediaController.releaseFuture(future)
            pending.finish()
        }, ContextCompat.getMainExecutor(context))
    }

    companion object { const val ACTION_TOGGLE = "com.everettpenne.spice.action.PIP_TOGGLE" }
}
