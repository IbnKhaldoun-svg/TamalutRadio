package com.tamalut.radio.feature.library

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.playback.TamalutPlaybackService
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalPlaybackItem(
    val mediaId: String,
    val contentUri: String,
    val title: String,
    val mimeType: String?,
)

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
                    mediaId = track.id.value,
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
    val currentMediaId: StateFlow<MediaId?>

    fun play(
        tracks: List<LocalAudioTrack>,
        selectedTrackId: MediaId,
        onResult: (Result<Unit>) -> Unit,
    )

    fun release() = Unit
}

object NoOpLocalPlaybackGateway : LocalPlaybackGateway {
    private val current = MutableStateFlow<MediaId?>(null)
    override val currentMediaId: StateFlow<MediaId?> = current.asStateFlow()

    override fun play(
        tracks: List<LocalAudioTrack>,
        selectedTrackId: MediaId,
        onResult: (Result<Unit>) -> Unit,
    ) {
        onResult(Result.failure(IllegalStateException("Local playback gateway is not configured")))
    }
}

class Media3LocalPlaybackGateway(
    context: Context,
) : LocalPlaybackGateway {
    private val appContext = context.applicationContext
    private val mainExecutor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }
    private val _currentMediaId = MutableStateFlow<MediaId?>(null)
    override val currentMediaId: StateFlow<MediaId?> = _currentMediaId.asStateFlow()

    private var browser: MediaBrowser? = null
    private var generation: Long = 0

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaId.value = mediaItem.toDomainMediaId()
        }
    }

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

        val requestGeneration = ++generation
        val token = SessionToken(
            appContext,
            ComponentName(appContext, TamalutPlaybackService::class.java),
        )
        val future = MediaBrowser.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { connectedBrowser ->
                        if (requestGeneration != generation) {
                            connectedBrowser.release()
                            return@onSuccess
                        }
                        runCatching {
                            browser?.removeListener(listener)
                            browser?.release()
                            browser = connectedBrowser
                            connectedBrowser.addListener(listener)
                            _currentMediaId.value = connectedBrowser.currentMediaItem.toDomainMediaId()
                            connectedBrowser.setMediaItems(
                                queue.items.map(LocalPlaybackItem::toMediaItem),
                                queue.startIndex,
                                0L,
                            )
                            connectedBrowser.prepare()
                            connectedBrowser.play()
                        }.onSuccess {
                            _currentMediaId.value = connectedBrowser.currentMediaItem.toDomainMediaId()
                            onResult(Result.success(Unit))
                        }.onFailure { error ->
                            onResult(Result.failure(error))
                        }
                    }
                    .onFailure { error ->
                        if (requestGeneration == generation) {
                            onResult(Result.failure(error))
                        }
                    }
            },
            mainExecutor,
        )
    }

    override fun release() {
        generation += 1
        browser?.removeListener(listener)
        browser?.release()
        browser = null
        _currentMediaId.value = null
    }
}

private fun LocalPlaybackItem.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .build(),
        )
    mimeType?.let(builder::setMimeType)
    return builder.build()
}

private fun MediaItem?.toDomainMediaId(): MediaId? = this
    ?.mediaId
    ?.takeIf(String::isNotBlank)
    ?.let(::MediaId)
