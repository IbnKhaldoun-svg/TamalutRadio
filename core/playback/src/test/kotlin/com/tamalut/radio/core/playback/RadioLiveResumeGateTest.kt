package com.tamalut.radio.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioLiveResumeGateTest {
    @Test
    fun initialRadioStartDoesNotReconnect() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = true, playWhenReady = false)

        assertFalse(gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = true))
    }

    @Test
    fun radioPauseThenResumeReconnects() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = true, playWhenReady = true)
        assertFalse(gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = false))

        assertTrue(gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = true))
        assertFalse(gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = true))
    }

    @Test
    fun localPauseResumeNeverReconnects() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = false, playWhenReady = true)
        assertFalse(gate.onPlayWhenReadyChanged(isRadio = false, playWhenReady = false))
        assertFalse(gate.onPlayWhenReadyChanged(isRadio = false, playWhenReady = true))
    }

    @Test
    fun switchingToLocalClearsPendingRadioReconnect() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = true, playWhenReady = true)
        gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = false)

        gate.onMediaItemChanged(isRadio = false, playWhenReady = false)

        assertFalse(gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = true))
    }

    @Test
    fun radioFallbackTransitionWhilePausedKeepsReconnectPending() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = true, playWhenReady = true)
        gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = false)

        gate.onMediaItemChanged(isRadio = true, playWhenReady = false)

        assertTrue(gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = true))
    }
}
