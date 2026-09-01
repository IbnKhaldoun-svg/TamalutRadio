package com.tamalut.radio.core.playback

import androidx.media3.common.Player
import com.tamalut.radio.core.model.MediaSourceType

internal object RadioQueuePolicy {
    fun repeatModeForQueueSize(queueSize: Int): Int {
        require(queueSize >= 1) { "Radio queue must contain at least one station" }
        return if (queueSize > 1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    fun hasMeaningfulSkip(queueSize: Int): Boolean = queueSize > 1

    fun exposedRepeatMode(
        sourceType: MediaSourceType?,
        playerRepeatMode: Int,
    ): PlaybackRepeatMode {
        if (sourceType == MediaSourceType.RADIO) return PlaybackRepeatMode.OFF
        return when (playerRepeatMode) {
            Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.ONE
            Player.REPEAT_MODE_ALL -> PlaybackRepeatMode.ALL
            else -> PlaybackRepeatMode.OFF
        }
    }

    fun exposedShuffle(
        sourceType: MediaSourceType?,
        playerShuffleEnabled: Boolean,
    ): Boolean = sourceType == MediaSourceType.LOCAL && playerShuffleEnabled
}
