package com.tamalut.radio.core.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackRepeatMode {
    OFF,
    ONE,
    ALL,
}

data class PlaybackState(
    val isConnected: Boolean = false,
    val sourceType: MediaSourceType? = null,
    val mediaId: MediaId? = null,
    val stationId: StationId? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
) {
    val hasCurrentItem: Boolean
        get() = sourceType != null && (mediaId != null || stationId != null)
}

data class LocalPlaybackItem(
    val mediaId: MediaId,
    val contentUri: String,
    val title: String,
    val mimeType: String?,
)

interface PlaybackController {
    val state: StateFlow<PlaybackState>

    fun playRadio(
        station: RadioStation,
        onResult: (Result<Unit>) -> Unit,
    )

    fun playRadioQueue(
        stations: List<RadioStation>,
        startIndex: Int,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (stations.isEmpty() || startIndex !in stations.indices) {
            onResult(Result.failure(IllegalArgumentException("Invalid radio playback queue")))
            return
        }
        playRadio(stations[startIndex], onResult)
    }

    fun playLocal(
        items: List<LocalPlaybackItem>,
        startIndex: Int,
        onResult: (Result<Unit>) -> Unit,
    )

    fun togglePlayPause()
    fun skipToPrevious()
    fun skipToNext()
    fun setLocalRepeatMode(mode: PlaybackRepeatMode)
    fun setLocalShuffleEnabled(enabled: Boolean)
    fun stopPlayback() = Unit
    fun stopAndExit(onResult: (Result<Unit>) -> Unit = {}) {
        stopPlayback()
        onResult(Result.success(Unit))
    }
    fun release()
}

object PlaybackModePolicy {
    fun repeatModeFor(
        sourceType: MediaSourceType?,
        mode: PlaybackRepeatMode,
    ): Int? = if (sourceType == MediaSourceType.LOCAL) {
        when (mode) {
            PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
            PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    } else {
        null
    }

    fun defaultRepeatModeForLocalQueue(): Int = Player.REPEAT_MODE_ALL

    fun nextRepeatMode(mode: PlaybackRepeatMode): PlaybackRepeatMode = when (mode) {
        PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
        PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
        PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
    }

    fun shuffleFor(
        sourceType: MediaSourceType?,
        enabled: Boolean,
    ): Boolean? = enabled.takeIf { sourceType == MediaSourceType.LOCAL }
}

internal inline fun installNewLocalQueue(
    setItems: () -> Unit,
    applyRepeatDefault: () -> Unit,
    prepare: () -> Unit,
    play: () -> Unit,
) {
    setItems()
    applyRepeatDefault()
    prepare()
    play()
}

class Media3PlaybackController(
    context: Context,
) : PlaybackController {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private data class PendingOperation(
        val action: (MediaBrowser) -> Unit,
        val onResult: (Result<Unit>) -> Unit,
    )

    private val pendingOperations = mutableListOf<PendingOperation>()
    private var browser: MediaBrowser? = null
    private var connecting = false
    private var released = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    init {
        mainHandler.post(::ensureConnected)
    }

    override fun playRadio(
        station: RadioStation,
        onResult: (Result<Unit>) -> Unit,
    ) {
        playRadioQueue(listOf(station), startIndex = 0, onResult = onResult)
    }

    override fun playRadioQueue(
        stations: List<RadioStation>,
        startIndex: Int,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (stations.isEmpty() || startIndex !in stations.indices) {
            onResult(Result.failure(IllegalArgumentException("Invalid radio playback queue")))
            return
        }
        execute(onResult) { connectedBrowser ->
            connectedBrowser.shuffleModeEnabled = false
            connectedBrowser.setMediaItems(
                stations.map(RadioMediaItemFactory::create),
                startIndex,
                0L,
            )
            connectedBrowser.repeatMode = RadioQueuePolicy.repeatModeForQueueSize(stations.size)
            connectedBrowser.prepare()
            connectedBrowser.play()
        }
    }

    override fun playLocal(
        items: List<LocalPlaybackItem>,
        startIndex: Int,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (items.isEmpty() || startIndex !in items.indices) {
            onResult(Result.failure(IllegalArgumentException("Invalid local playback queue")))
            return
        }
        execute(onResult) { connectedBrowser ->
            installNewLocalQueue(
                setItems = {
                    connectedBrowser.setMediaItems(
                        items.map(LocalPlaybackItem::toMediaItem),
                        startIndex,
                        0L,
                    )
                },
                applyRepeatDefault = {
                    connectedBrowser.repeatMode = PlaybackModePolicy.defaultRepeatModeForLocalQueue()
                },
                prepare = connectedBrowser::prepare,
                play = connectedBrowser::play,
            )
        }
    }

    override fun togglePlayPause() {
        executeSilently { connectedBrowser ->
            if (connectedBrowser.isPlaying) connectedBrowser.pause() else connectedBrowser.play()
        }
    }

    override fun skipToPrevious() {
        executeSilently { connectedBrowser ->
            if (connectedBrowser.hasPreviousMediaItem()) {
                connectedBrowser.seekToPreviousMediaItem()
            }
        }
    }

    override fun skipToNext() {
        executeSilently { connectedBrowser ->
            if (connectedBrowser.hasNextMediaItem()) {
                connectedBrowser.seekToNextMediaItem()
            }
        }
    }

    override fun setLocalRepeatMode(mode: PlaybackRepeatMode) {
        val media3Mode = PlaybackModePolicy.repeatModeFor(_state.value.sourceType, mode) ?: return
        executeSilently { connectedBrowser ->
            connectedBrowser.repeatMode = media3Mode
        }
    }

    override fun setLocalShuffleEnabled(enabled: Boolean) {
        val media3Shuffle = PlaybackModePolicy.shuffleFor(_state.value.sourceType, enabled) ?: return
        executeSilently { connectedBrowser ->
            connectedBrowser.shuffleModeEnabled = media3Shuffle
        }
    }

    override fun stopPlayback() {
        executeSilently { connectedBrowser -> connectedBrowser.stop() }
    }

    override fun stopAndExit(onResult: (Result<Unit>) -> Unit) {
        execute(
            onResult = { operationResult ->
                if (operationResult.isFailure) onResult(operationResult)
            },
        ) { connectedBrowser ->
            val future = runCatching {
                connectedBrowser.sendCustomCommand(PlaybackCommands.stopExitCommand, Bundle.EMPTY)
            }.getOrElse { error ->
                connectedBrowser.stop()
                connectedBrowser.clearMediaItems()
                onResult(Result.failure(error))
                return@execute
            }
            future.addListener(
                {
                    val outcome = runCatching { future.get() }
                        .mapCatching { sessionResult ->
                            check(sessionResult.resultCode == SessionResult.RESULT_SUCCESS) {
                                "STOP_EXIT command failed: ${sessionResult.resultCode}"
                            }
                        }
                        .map { Unit }
                    if (outcome.isFailure) {
                        runCatching {
                            connectedBrowser.stop()
                            connectedBrowser.clearMediaItems()
                        }
                    }
                    onResult(outcome)
                },
                mainExecutor,
            )
        }
    }

    private fun executeSilently(action: (MediaBrowser) -> Unit) {
        execute(onResult = {}) { connectedBrowser -> action(connectedBrowser) }
    }

    private fun execute(
        onResult: (Result<Unit>) -> Unit,
        action: (MediaBrowser) -> Unit,
    ) {
        mainHandler.post {
            if (released) {
                onResult(Result.failure(IllegalStateException("Playback controller is released")))
                return@post
            }
            val connectedBrowser = browser
            if (connectedBrowser != null) {
                runOperation(connectedBrowser, action, onResult)
            } else {
                pendingOperations += PendingOperation(action, onResult)
                ensureConnected()
            }
        }
    }

    private fun ensureConnected() {
        if (released || connecting || browser != null) return
        connecting = true
        val token = SessionToken(
            appContext,
            ComponentName(appContext, TamalutPlaybackService::class.java),
        )
        val future = MediaBrowser.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                connecting = false
                runCatching { future.get() }
                    .onSuccess { connectedBrowser ->
                        if (released) {
                            connectedBrowser.release()
                            failPending(IllegalStateException("Playback controller is released"))
                            return@onSuccess
                        }
                        browser = connectedBrowser
                        connectedBrowser.addListener(playerListener)
                        publish(connectedBrowser)
                        val operations = pendingOperations.toList()
                        pendingOperations.clear()
                        operations.forEach { operation ->
                            runOperation(
                                connectedBrowser = connectedBrowser,
                                action = operation.action,
                                onResult = operation.onResult,
                            )
                        }
                    }
                    .onFailure(::failPending)
            },
            mainExecutor,
        )
    }

    private fun runOperation(
        connectedBrowser: MediaBrowser,
        action: (MediaBrowser) -> Unit,
        onResult: (Result<Unit>) -> Unit,
    ) {
        runCatching { action(connectedBrowser) }
            .onSuccess {
                publish(connectedBrowser)
                onResult(Result.success(Unit))
            }
            .onFailure { error ->
                onResult(Result.failure(error))
            }
    }

    private fun failPending(error: Throwable) {
        val operations = pendingOperations.toList()
        pendingOperations.clear()
        operations.forEach { it.onResult(Result.failure(error)) }
        _state.value = PlaybackState()
    }

    private fun publish(player: Player) {
        val currentItem = player.currentMediaItem
        if (currentItem == null) {
            _state.value = PlaybackState(isConnected = true)
            return
        }

        val radioPlan = RadioMediaItemFactory.planFrom(currentItem)
        val sourceType = if (radioPlan != null) MediaSourceType.RADIO else MediaSourceType.LOCAL
        val localMediaId = if (sourceType == MediaSourceType.LOCAL) {
            currentItem.mediaId
                .takeIf(String::isNotBlank)
                ?.let { rawId -> runCatching { MediaId(rawId) }.getOrNull() }
        } else {
            null
        }
        val title = currentItem.mediaMetadata.title
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?: radioPlan?.stationName
            ?: localMediaId?.value

        _state.value = PlaybackState(
            isConnected = true,
            sourceType = sourceType,
            mediaId = localMediaId,
            stationId = radioPlan?.stationId,
            title = title,
            isPlaying = player.isPlaying,
            canSkipPrevious = if (sourceType == MediaSourceType.RADIO) {
                RadioQueuePolicy.hasMeaningfulSkip(player.mediaItemCount)
            } else {
                player.hasPreviousMediaItem()
            },
            canSkipNext = if (sourceType == MediaSourceType.RADIO) {
                RadioQueuePolicy.hasMeaningfulSkip(player.mediaItemCount)
            } else {
                player.hasNextMediaItem()
            },
            repeatMode = RadioQueuePolicy.exposedRepeatMode(sourceType, player.repeatMode),
            shuffleEnabled = RadioQueuePolicy.exposedShuffle(sourceType, player.shuffleModeEnabled),
        )
    }

    override fun release() {
        mainHandler.post {
            if (released) return@post
            released = true
            failPending(IllegalStateException("Playback controller is released"))
            browser?.removeListener(playerListener)
            browser?.release()
            browser = null
            _state.value = PlaybackState()
        }
    }
}

private fun LocalPlaybackItem.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setMediaId(mediaId.value)
        .setUri(contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .build(),
        )
    mimeType?.let(builder::setMimeType)
    return builder.build()
}
