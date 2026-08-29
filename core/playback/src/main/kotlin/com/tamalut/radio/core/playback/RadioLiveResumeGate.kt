package com.tamalut.radio.core.playback

import androidx.media3.common.Player

internal class RadioLiveResumeGate {
    private var radioHasPlayed = false
    private var pausedRadioPending = false
    private var transientFocusLossPending = false

    fun onMediaItemChanged(isRadio: Boolean, playWhenReady: Boolean) {
        if (!isRadio) {
            reset()
        } else if (playWhenReady) {
            radioHasPlayed = true
        }
    }

    fun onPlayWhenReadyChanged(isRadio: Boolean, playWhenReady: Boolean): Boolean {
        if (!isRadio) {
            reset()
            return false
        }

        if (!playWhenReady) {
            if (radioHasPlayed) pausedRadioPending = true
            return false
        }

        val shouldReconnect = pausedRadioPending
        pausedRadioPending = false
        radioHasPlayed = true
        return shouldReconnect
    }

    fun onPlaybackSuppressionReasonChanged(
        isRadio: Boolean,
        playbackSuppressionReason: Int,
        playWhenReady: Boolean,
    ): Boolean {
        if (!isRadio) {
            reset()
            return false
        }

        if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
            if (radioHasPlayed && playWhenReady) {
                transientFocusLossPending = true
            }
            return false
        }

        if (
            playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            transientFocusLossPending
        ) {
            transientFocusLossPending = false
            return playWhenReady
        }

        return false
    }

    fun reset() {
        radioHasPlayed = false
        pausedRadioPending = false
        transientFocusLossPending = false
    }
}
