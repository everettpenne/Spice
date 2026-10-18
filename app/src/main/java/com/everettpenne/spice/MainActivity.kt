package com.everettpenne.spice

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.everettpenne.spice.playback.PiP
import com.everettpenne.spice.playback.PipActionReceiver
import com.everettpenne.spice.playback.PlaybackStatus
import com.everettpenne.spice.ui.SpiceRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var inPip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        setContent { SpiceRoot() }
        // Keep the PiP play/pause icon in step with playback while the small window is showing.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackStatus.now.collect { if (inPip) setPictureInPictureParams(pipParams(it.isPlaying)) }
            }
        }
    }

    /** Leaving the app while a video plays shrinks it into a 16:9 picture-in-picture window; music keeps backgrounding as before. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val now = PlaybackStatus.now.value
        if (PiP.shouldEnterPip(now.itemType, now.isPlaying)) {
            enterPictureInPictureMode(pipParams(now.isPlaying))
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        PiP.setActive(isInPictureInPictureMode)
        // The PiP window is just the video: hide the full-screen activity's bars behind it, and bring them back on return.
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (isInPictureInPictureMode) bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
        // Clear the PiP actions when back in the full activity; they are rebuilt each time it re-enters.
        if (!isInPictureInPictureMode) setPictureInPictureParams(PictureInPictureParams.Builder().build())
    }

    private fun pipParams(playing: Boolean): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(listOf(pipPlayPauseAction(playing)))
        .build()

    private fun pipPlayPauseAction(playing: Boolean): RemoteAction {
        val intent = Intent(this, PipActionReceiver::class.java).setAction(PipActionReceiver.ACTION_TOGGLE)
        val pending = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val icon = Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        return RemoteAction(icon, if (playing) "Pause" else "Play", if (playing) "Pause" else "Play", pending)
    }
}
