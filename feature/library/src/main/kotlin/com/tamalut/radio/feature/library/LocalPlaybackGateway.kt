package com.tamalut.radio.feature.library

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.playback.LocalPlaybackItem
import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackRepeatMode
import com.tamalut.radio.core.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalPlaybackQueue(
    val items: List<LocalPlaybackItem>,
    val startIndex: Int,
)

object LocalPlaybackQueueFactory {
    fun create(
        tracks: List<LocalAudioTrack>,
        selectedTrackId: MediaId,
    ): LocalPlaybackQueue {
        require(tracks.isNotEmpty()) { "Local playback queue must not be empty" }
        val startIndex = tracks.indexOfFirst { it.id == selectedTrackId }
        require(startIndex >= 0) { "Selected local track is not present in the queue" }
        return LocalPlaybackQueue(
            items = tracks.map { track ->
                LocalPlaybackItem(
                    mediaId = track.id,
                    contentUri = track.contentUri,
                    title = track.title,
                    mimeType = track.mimeType,
                )
            },
            startIndex = startIndex,
        )
    }
}

interface LocalPlaybackGateway {
    val playbackState: StateFlow<PlaybackState>

    fun play(
        tracks: List<LocalAudioTrack>,
        selectedTrackId: MediaId,
        onResult: (Result<Unit>) -> Unit,
    )

    fun setRepeatMode(mode: PlaybackRepeatMode)
    fun setShuffleEnabled(enabled: Boolean)
    fun release() = Unit
}

object NoOpLocalPlaybackGateway : LocalPlaybackGateway {
    private val current = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = current.asStateFlow()

    override fun play(
        tracks: List<LocalAudioTrack>,
        selectedTrackId: MediaId,
        onResult: (Result<Unit>) -> Unit,
    ) {
        onResult(Result.failure(IllegalStateException("Local playback gateway is not configured")))
    }

    override fun setRepeatMode(mode: PlaybackRepeatMode) = Unit
    override fun setShuffleEnabled(enabled: Boolean) = Unit
}

class Media3LocalPlaybackGateway(
    private val playbackController: PlaybackController,
) : LocalPlaybackGateway {
    override val playbackState: StateFlow<PlaybackState> = playbackController.state

    override fun play(
        tracks: List<LocalAudioTrack>,
        selectedTrackId: MediaId,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val queue = runCatching {
            LocalPlaybackQueueFactory.create(tracks, selectedTrackId)
        }.getOrElse { error ->
            onResult(Result.failure(error))
            return
        }
        playbackController.playLocal(
            items = queue.items,
            startIndex = queue.startIndex,
            onResult = onResult,
        )
    }

    override fun setRepeatMode(mode: PlaybackRepeatMode) {
        playbackController.setLocalRepeatMode(mode)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        playbackController.setLocalShuffleEnabled(enabled)
    }
}
