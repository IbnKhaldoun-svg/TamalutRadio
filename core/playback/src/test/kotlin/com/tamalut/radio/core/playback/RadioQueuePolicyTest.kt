package com.tamalut.radio.core.playback

import androidx.media3.common.Player
import com.tamalut.radio.core.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioQueuePolicyTest {
    @Test
    fun multiStationQueueUsesInternalRepeatAllAndMeaningfulSkip() {
        assertEquals(Player.REPEAT_MODE_ALL, RadioQueuePolicy.repeatModeForQueueSize(2))
        assertTrue(RadioQueuePolicy.hasMeaningfulSkip(2))
    }

    @Test
    fun oneStationQueueDisablesSkipAndInternalLoop() {
        assertEquals(Player.REPEAT_MODE_OFF, RadioQueuePolicy.repeatModeForQueueSize(1))
        assertFalse(RadioQueuePolicy.hasMeaningfulSkip(1))
    }

    @Test
    fun internalRadioLoopDoesNotLeakAsUserRepeatOrShuffle() {
        assertEquals(
            PlaybackRepeatMode.OFF,
            RadioQueuePolicy.exposedRepeatMode(MediaSourceType.RADIO, Player.REPEAT_MODE_ALL),
        )
        assertFalse(RadioQueuePolicy.exposedShuffle(MediaSourceType.RADIO, true))
    }

    @Test
    fun localModeProjectionIsUnchanged() {
        assertEquals(
            PlaybackRepeatMode.ALL,
            RadioQueuePolicy.exposedRepeatMode(MediaSourceType.LOCAL, Player.REPEAT_MODE_ALL),
        )
        assertEquals(
            PlaybackRepeatMode.ONE,
            RadioQueuePolicy.exposedRepeatMode(MediaSourceType.LOCAL, Player.REPEAT_MODE_ONE),
        )
        assertTrue(RadioQueuePolicy.exposedShuffle(MediaSourceType.LOCAL, true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyQueueIsRejected() {
        RadioQueuePolicy.repeatModeForQueueSize(0)
    }
}
