package com.everettpenne.spice.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiPTest {
    @Test fun `video item types are PiP eligible`() {
        listOf("Movie", "Episode", "Video", "MusicVideo").forEach { type ->
            assertTrue("$type should be a video type", PiP.isVideoType(type))
        }
    }

    @Test fun `audio and non-video types are not PiP eligible`() {
        listOf("Audio", "MusicAlbum", "MusicArtist", "Series", "Season", "Folder", "", null).forEach { type ->
            assertFalse("$type should not be a video type", PiP.isVideoType(type))
        }
    }

    @Test fun `PiP is entered only for a video that is actually playing`() {
        assertTrue(PiP.shouldEnterPip("Movie", isPlaying = true))
        assertTrue(PiP.shouldEnterPip("Episode", isPlaying = true))
        assertTrue(PiP.shouldEnterPip("MusicVideo", isPlaying = true))
        assertFalse("a paused video must not enter PiP", PiP.shouldEnterPip("Movie", isPlaying = false))
        assertFalse("audio must never enter PiP", PiP.shouldEnterPip("Audio", isPlaying = true))
        assertFalse("an unknown or missing type must not enter PiP", PiP.shouldEnterPip(null, isPlaying = true))
    }
}
