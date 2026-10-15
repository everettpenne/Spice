package com.everettpenne.spice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Drives the real app through its main path against tools/fake-jellyfin/server.py (run it on the host: the emulator reaches it at 10.0.2.2:8096):
 * connect, sign in, home shelves, library, detail, playback. Saves a screenshot at each step to the app's files/shots for a look.
 */
class FlowAgainstFakeServerTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(1500); device.takeScreenshot(File(dir, "$name.png")) }
    private fun waitFor(text: String, timeout: Long = 40_000) {
        try { rule.waitUntil(timeout) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { shot("FAILED-waiting-for-" + text.replace(" ", "-")); throw e }
    }
    private fun clickButton(text: String) = rule.onAllNodesWithText(text).filterToOne(hasClickAction()).performClick()

    @Test fun connectBrowseAndPlay() {
        val graph = InstrumentationRegistry.getInstrumentation().targetContext.graph
        graph.sessions.signOut()
        waitFor("Your Jellyfin server"); shot("1-connect")
        rule.onNode(hasSetTextAction()).performTextInput("10.0.2.2:8096")
        clickButton("Connect")
        waitFor("Fake Jellyfin"); shot("2-signin")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("demo")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("demo")
        clickButton("Sign in")
        waitFor("Continue Watching"); shot("3-home")
        rule.onNodeWithContentDescription("Library").performClick()
        waitFor("Favorites"); shot("4-library")
        rule.onNodeWithText("Movies").performClick()
        waitFor("Blade Runner 2049"); shot("5-browse")
        rule.onNodeWithText("Dune").performClick()
        waitFor("Play"); shot("6-detail")
        clickButton("Play")
        waitFor("Direct", 60_000); Thread.sleep(6000); shot("7-player")
    }
}
