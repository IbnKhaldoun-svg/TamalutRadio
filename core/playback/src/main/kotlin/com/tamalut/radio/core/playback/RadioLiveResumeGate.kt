package com.tamalut.radio.core.playback

internal class RadioLiveResumeGate {
    private var radioHasPlayed = false
    private var pausedRadioPending = false

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

    fun reset() {
        radioHasPlayed = false
        pausedRadioPending = false
    }
}
