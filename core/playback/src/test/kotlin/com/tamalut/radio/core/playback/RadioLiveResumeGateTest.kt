package com.tamalut.radio.core.playback

import androidx.media3.common.Player
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

    @Test
    fun radioTransientAudioFocusLossThenGainReconnects() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = true, playWhenReady = true)

        assertFalse(
            gate.onPlaybackSuppressionReasonChanged(
                isRadio = true,
                playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
                playWhenReady = true,
            ),
        )
        assertTrue(
            gate.onPlaybackSuppressionReasonChanged(
                isRadio = true,
                playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                playWhenReady = true,
            ),
        )
        assertFalse(
            gate.onPlaybackSuppressionReasonChanged(
                isRadio = true,
                playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun localTransientAudioFocusLossThenGainNeverReconnects() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = false, playWhenReady = true)

        assertFalse(
            gate.onPlaybackSuppressionReasonChanged(
                isRadio = false,
                playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
                playWhenReady = true,
            ),
        )
        assertFalse(
            gate.onPlaybackSuppressionReasonChanged(
                isRadio = false,
                playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun audioFocusGainWithoutPriorTransientLossDoesNotReconnect() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = true, playWhenReady = true)

        assertFalse(
            gate.onPlaybackSuppressionReasonChanged(
                isRadio = true,
                playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun manualPauseDuringTransientFocusLossDoesNotAutoResumeOnGain() {
        val gate = RadioLiveResumeGate()
        gate.onMediaItemChanged(isRadio = true, playWhenReady = true)
        gate.onPlaybackSuppressionReasonChanged(
            isRadio = true,
            playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
            playWhenReady = true,
        )
        gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = false)

        assertFalse(
            gate.onPlaybackSuppressionReasonChanged(
                isRadio = true,
                playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                playWhenReady = false,
            ),
        )
        assertTrue(gate.onPlayWhenReadyChanged(isRadio = true, playWhenReady = true))
    }
}
