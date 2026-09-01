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

    @Test
    fun radioPlaybackErrorActionMatchesOnlyExplicitCommand() {
        assertTrue(
            PlaybackCommands.isRadioPlaybackError(PlaybackCommands.RADIO_PLAYBACK_ERROR_ACTION),
        )
        assertFalse(PlaybackCommands.isRadioPlaybackError(PlaybackCommands.STOP_EXIT_ACTION))
    }
}
