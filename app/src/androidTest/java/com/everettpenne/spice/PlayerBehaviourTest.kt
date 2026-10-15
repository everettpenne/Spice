package com.everettpenne.spice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.everettpenne.spice.data.auth.Session
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * How the player behaves, against tools/fake-jellyfin/server.py (run on the host; the emulator reaches it at 10.0.2.2:8096): leaving it,
 * portrait and landscape layouts, a file whose audio the phone cannot decode, and playing on with the app in the background.
 */
class PlayerBehaviourTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }

    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(1500); device.takeScreenshot(File(dir, "$name.png")) }
    private fun has(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String, timeout: Long = 60_000) {
        try { rule.waitUntil(timeout) { has(text) } } catch (e: Throwable) { shot("FAILED-waiting-for-" + text.replace(" ", "-")); throw e }
    }
    private fun waitGone(text: String, timeout: Long = 30_000) = rule.waitUntil(timeout) { !has(text) }
    private fun shell(cmd: String) = device.executeShellCommand(cmd)

    @Before fun signIn() {
        // The player asks to show its notification the first time; a system dialog over the app would hide it from the test.
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, "android.permission.POST_NOTIFICATIONS")
        instrumentation.targetContext.graph.sessions.signIn(Session("http://10.0.2.2:8096", "u1", "demo", "tok-demo", "Fake Jellyfin"))
        waitFor("Continue Watching")
    }
    @After fun upright() { device.setOrientationNatural(); device.unfreezeRotation() }

    private fun openMovie(name: String) {
        rule.onNodeWithContentDescription("Library").performClick(); waitFor("Favorites")
        rule.onNodeWithText("Movies").performClick(); waitFor(name)
        rule.onNodeWithText(name).performClick(); waitFor("Play")
        rule.onAllNodesWithText("Play").filterToOne(hasClickAction()).performClick()
    }

    private fun serviceIsForeground() = shell("dumpsys activity services com.everettpenne.spice").let { it.contains("PlaybackService") && it.contains("isForeground=true") }

    @Test fun backButtonLeavesThePlayerAndStopsPlayback() {
        openMovie("Dune"); waitFor("Direct")
        rule.onNodeWithContentDescription("Back").performTouchInput { click() }      // a real touch, not a semantic click
        waitFor("More Like This", 20_000)                                            // the detail page again
        rule.waitUntil(20_000) { !serviceIsForeground() }                            // and nothing is left playing
    }

    @Test fun portraitHasVideoOnTopAndDetailsBelow() {
        openMovie("Dune"); waitFor("Direct")
        waitFor("More Like This"); shot("p1-movie-portrait")
        assertTrue(has("Sci-Fi · Drama"))
    }

    @Test fun anEpisodeShowsItsSeasonsAndOtherEpisodes() {
        rule.onNodeWithContentDescription("Library").performClick(); waitFor("Favorites")
        rule.onNodeWithText("Shows").performClick(); waitFor("Severance")
        rule.onNodeWithText("Severance").performClick()
        rule.waitUntil(60_000) { rule.onAllNodes(hasText("Play", substring = true) or hasText("Resume", substring = true)).fetchSemanticsNodes().isNotEmpty() }   // "Play S1 E3", or Resume when one is in progress
        rule.onAllNodes(hasText("Play", substring = true) or hasText("Resume", substring = true)).filterToOne(hasClickAction()).performClick()
        waitFor("Direct"); waitFor("More Like This")
        waitFor("1. Episode title 1"); shot("p2-episode-portrait")          // the season's other episodes, under the video
        assertTrue(rule.onAllNodes(hasText("2 seasons", substring = true)).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("1. Episode title 1").performClick()            // tapping another episode switches to it
        waitFor("S1 E1 · Episode title 1")
    }

    @Test fun rotatingToLandscapeIsFullScreenAndBackAgain() {
        openMovie("Dune"); waitFor("Direct"); waitFor("More Like This")
        device.setOrientationLeft(); waitGone("More Like This"); shot("l1-landscape")
        assertTrue(has("Direct") || has("Transcoding") || rule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty())
        device.setOrientationNatural(); waitFor("More Like This"); shot("l2-portrait-again")
    }

    @Test fun audioThePhoneCannotDecodeFallsBackToATranscode() {
        openMovie("Needs Transcoding")
        waitFor("Transcoding", 90_000); shot("a1-transcoding")
    }

    private fun sessionPositionMs() = Regex("""PlaybackState \{state=PLAYING\(3\), position=(\d+)""").find(shell("dumpsys media_session"))?.groupValues?.get(1)?.toLong()
    private fun audioIsStarted() = shell("dumpsys audio").lines().any { it.contains("AudioPlaybackConfiguration") && it.contains("state:started") && it.contains("USAGE_MEDIA") && it.contains("CONTENT_TYPE_MOVIE") && it.contains(" u/pid:${instrumentation.targetContext.applicationInfo.uid}/") }

    @Test fun soundContinuesWhenTheAppIsInTheBackground() {
        openMovie("Dune"); waitFor("Direct")
        Thread.sleep(3000)
        device.pressHome(); Thread.sleep(5000)
        assertTrue("a foreground service should keep playing\n" + shell("dumpsys activity services com.everettpenne.spice").take(1200), serviceIsForeground())
        val first = sessionPositionMs()
        assertTrue("the media session should be playing with the app in the background:\n" + shell("dumpsys media_session").lines().filter { it.contains("state=") }.take(4), first != null)
        Thread.sleep(3000)
        val second = sessionPositionMs()
        assertTrue("playback should advance in the background ($first -> $second)", second != null && second > first!! + 1500)
        shell("am start -n com.everettpenne.spice/.MainActivity"); waitFor("Direct")      // back in the app, still the same playback
    }

    @Test fun audioOutputIsActuallyStarted() {
        openMovie("Dune"); waitFor("Direct"); Thread.sleep(4000)
        assertTrue("an audio track for Spice should be started:\n" + shell("dumpsys audio").lines().filter { it.contains("AudioPlaybackConfiguration") }.take(6).joinToString("\n"), audioIsStarted())
    }
}
