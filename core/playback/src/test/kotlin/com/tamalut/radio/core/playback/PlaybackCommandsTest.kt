package com.tamalut.radio.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCommandsTest {
    @Test
    fun stopExitActionMatchesOnlyExplicitCommand() {
        assertTrue(PlaybackCommands.isStopExit(PlaybackCommands.STOP_EXIT_ACTION))
        assertFalse(PlaybackCommands.isStopExit("com.tamalut.radio.playback.OTHER"))
    }
}
