package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow

interface RadioPlaybackGateway {
    val playbackState: StateFlow<PlaybackState>

    fun play(
        stations: List<RadioStation>,
        startIndex: Int,
        onResult: (Result<Unit>) -> Unit,
    )
    fun release() = Unit
}

class Media3RadioPlaybackGateway(
    private val playbackController: PlaybackController,
) : RadioPlaybackGateway {
    override val playbackState: StateFlow<PlaybackState> = playbackController.state

    override fun play(
        stations: List<RadioStation>,
        startIndex: Int,
        onResult: (Result<Unit>) -> Unit,
    ) {
        playbackController.playRadioQueue(stations, startIndex, onResult)
    }
}
