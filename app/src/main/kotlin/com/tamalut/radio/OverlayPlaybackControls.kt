package com.tamalut.radio

import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackState

internal enum class OverlayPlaybackAction {
    PREVIOUS,
    TOGGLE_PLAY_PAUSE,
    NEXT,
    STOP,
}

internal enum class OverlayPlayPauseIcon {
    PLAY,
    PAUSE,
}

internal data class OverlayPlaybackControlsModel(
    val playPauseIcon: OverlayPlayPauseIcon,
    val previousEnabled: Boolean,
    val nextEnabled: Boolean,
)

internal fun PlaybackState.toOverlayPlaybackControlsModel(): OverlayPlaybackControlsModel? {
    if (!hasCurrentItem) return null
    return OverlayPlaybackControlsModel(
        playPauseIcon = if (isPlaying) OverlayPlayPauseIcon.PAUSE else OverlayPlayPauseIcon.PLAY,
        previousEnabled = canSkipPrevious,
        nextEnabled = canSkipNext,
    )
}

internal fun performOverlayPlaybackAction(
    action: OverlayPlaybackAction,
    state: PlaybackState,
    controller: PlaybackController,
    onStop: () -> Unit = {},
) {
    when (action) {
        OverlayPlaybackAction.PREVIOUS -> if (state.canSkipPrevious) controller.skipToPrevious()
        OverlayPlaybackAction.TOGGLE_PLAY_PAUSE -> if (state.hasCurrentItem) controller.togglePlayPause()
        OverlayPlaybackAction.NEXT -> if (state.canSkipNext) controller.skipToNext()
        OverlayPlaybackAction.STOP -> if (state.hasCurrentItem) onStop()
    }
}

internal fun performOverlayAppEntry(openApp: () -> Unit) {
    openApp()
}
