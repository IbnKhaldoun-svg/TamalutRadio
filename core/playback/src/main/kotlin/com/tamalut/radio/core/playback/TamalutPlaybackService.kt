package com.tamalut.radio.core.playback

import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class TamalutPlaybackService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val liveResumeGate = RadioLiveResumeGate()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sleepTimerPresentationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sleepTimerPresentationJob: Job? = null
    private var sleepTimerRemainingSeconds: Long? = null
    private var sleepTimerOriginalItem: MediaItem? = null
    private var sleepTimerDecoratedItem: MediaItem? = null
    private var sleepTimerDecoratedIndex: Int = -1
    private var applyingSleepTimerMetadata = false
    private var pendingSleepTimerPresentationItem: MediaItem? = null

    var currentRadioFallbackState: RadioFallbackState = RadioFallbackState.Inactive
        private set

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (applyingSleepTimerMetadata || mediaItem == pendingSleepTimerPresentationItem) {
                if (mediaItem == pendingSleepTimerPresentationItem) {
                    pendingSleepTimerPresentationItem = null
                }
                return
            }
            pendingSleepTimerPresentationItem = null
            val exoPlayer = player
            if (exoPlayer != null) {
                restoreSleepTimerMetadata(exoPlayer)
            }
            val plan = RadioMediaItemFactory.planFrom(mediaItem)
            liveResumeGate.onMediaItemChanged(
                isRadio = plan != null,
                playWhenReady = exoPlayer?.playWhenReady == true,
            )
            currentRadioFallbackState = plan?.asState() ?: RadioFallbackState.Inactive
            exoPlayer?.let(::applySleepTimerMetadata)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val exoPlayer = player ?: return
            val currentItem = exoPlayer.currentMediaItem
            val plan = RadioMediaItemFactory.planFrom(currentItem)
            val shouldReconnect = liveResumeGate.onPlayWhenReadyChanged(
                isRadio = plan != null,
                playWhenReady = playWhenReady,
            )
            if (shouldReconnect && currentItem != null && plan != null) {
                reconnectCurrentRadioAtLiveEdge(exoPlayer, plan)
            }
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            val exoPlayer = player ?: return
            val currentItem = exoPlayer.currentMediaItem
            val plan = RadioMediaItemFactory.planFrom(currentItem)
            val shouldReconnect = liveResumeGate.onPlaybackSuppressionReasonChanged(
                isRadio = plan != null,
                playbackSuppressionReason = playbackSuppressionReason,
                playWhenReady = exoPlayer.playWhenReady,
            )
            if (shouldReconnect && currentItem != null && plan != null) {
                reconnectCurrentRadioAtLiveEdge(exoPlayer, plan)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val exoPlayer = player ?: return
            val plan = RadioMediaItemFactory.planFrom(exoPlayer.currentMediaItem) ?: return

            when (val decision = plan.onFatalError(error.errorCode)) {
                is RadioFallbackDecision.Retry -> {
                    val preservePlayIntent = exoPlayer.playWhenReady
                    restoreSleepTimerMetadata(exoPlayer)
                    val currentIndex = exoPlayer.currentMediaItemIndex
                    if (currentIndex !in 0 until exoPlayer.mediaItemCount) return
                    currentRadioFallbackState = decision.plan.asState()
                    exoPlayer.replaceMediaItem(
                        currentIndex,
                        RadioMediaItemFactory.create(decision.plan),
                    )
                    exoPlayer.seekToDefaultPosition(currentIndex)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = preservePlayIntent
                }

                is RadioFallbackDecision.Exhausted -> {
                    currentRadioFallbackState = decision.state
                    mediaLibrarySession?.broadcastCustomCommand(
                        PlaybackCommands.radioPlaybackErrorCommand,
                        PlaybackCommands.radioPlaybackErrorArgs(
                            stationId = decision.state.stationId,
                            errorCode = error.errorCode,
                        ),
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .build()
            .also { it.addListener(playerListener) }
        player = exoPlayer
        val sessionBuilder = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            PlaybackSessionCallback(::stopAndExit),
        ).setMediaButtonPreferences(PlaybackControls.mediaButtonPreferences(includeStopExit = true))
        PlaybackLaunchContract.createNowPlayingPendingIntent(this)?.let { pendingIntent ->
            sessionBuilder.setSessionActivity(pendingIntent)
        }
        mediaLibrarySession = sessionBuilder.build()
        sleepTimerPresentationJob = sleepTimerPresentationScope.launch {
            SleepTimerNotificationBridge.remainingSeconds.collect { remainingSeconds ->
                mainHandler.post {
                    sleepTimerRemainingSeconds = remainingSeconds
                    player?.let(::applySleepTimerMetadata)
                }
            }
        }
    }

    private fun applySleepTimerMetadata(exoPlayer: ExoPlayer) {
        val contentText = sleepTimerNotificationContentText(sleepTimerRemainingSeconds)
        if (contentText == null) {
            restoreSleepTimerMetadata(exoPlayer)
            return
        }
        val currentItem = exoPlayer.currentMediaItem ?: run {
            clearSleepTimerMetadataTracking()
            return
        }
        val currentIndex = exoPlayer.currentMediaItemIndex
        val trackedCurrent = sleepTimerDecoratedIndex == currentIndex && currentItem == sleepTimerDecoratedItem
        if (!trackedCurrent) {
            restoreSleepTimerMetadata(exoPlayer)
            val freshItem = exoPlayer.currentMediaItem ?: return
            sleepTimerOriginalItem = freshItem
            sleepTimerDecoratedIndex = exoPlayer.currentMediaItemIndex
        }
        val originalItem = sleepTimerOriginalItem ?: return
        val decoratedItem = originalItem.buildUpon()
            .setMediaMetadata(
                originalItem.mediaMetadata.buildUpon()
                    .setArtist(contentText)
                    .build(),
            )
            .build()
        if (exoPlayer.currentMediaItem == decoratedItem) {
            sleepTimerDecoratedItem = decoratedItem
            return
        }
        replaceMediaItemForSleepTimer(
            exoPlayer = exoPlayer,
            index = sleepTimerDecoratedIndex,
            replacement = decoratedItem,
        )
        sleepTimerDecoratedItem = decoratedItem
    }

    private fun restoreSleepTimerMetadata(exoPlayer: ExoPlayer) {
        val originalItem = sleepTimerOriginalItem
        val decoratedItem = sleepTimerDecoratedItem
        val index = sleepTimerDecoratedIndex
        if (
            originalItem != null &&
            decoratedItem != null &&
            index in 0 until exoPlayer.mediaItemCount &&
            exoPlayer.getMediaItemAt(index) == decoratedItem
        ) {
            replaceMediaItemForSleepTimer(exoPlayer, index, originalItem)
        }
        clearSleepTimerMetadataTracking()
    }

    private fun replaceMediaItemForSleepTimer(
        exoPlayer: ExoPlayer,
        index: Int,
        replacement: MediaItem,
    ) {
        if (index !in 0 until exoPlayer.mediaItemCount) return
        pendingSleepTimerPresentationItem = replacement
        applyingSleepTimerMetadata = true
        try {
            exoPlayer.replaceMediaItem(index, replacement)
        } finally {
            applyingSleepTimerMetadata = false
        }
        mainHandler.post {
            if (pendingSleepTimerPresentationItem == replacement) {
                pendingSleepTimerPresentationItem = null
            }
        }
    }

    private fun clearSleepTimerMetadataTracking() {
        sleepTimerOriginalItem = null
        sleepTimerDecoratedItem = null
        sleepTimerDecoratedIndex = -1
    }

    private fun reconnectCurrentRadioAtLiveEdge(
        exoPlayer: ExoPlayer,
        plan: RadioFallbackPlan,
    ) {
        val currentIndex = exoPlayer.currentMediaItemIndex
        if (currentIndex !in 0 until exoPlayer.mediaItemCount) return
        currentRadioFallbackState = plan.asState()
        restoreSleepTimerMetadata(exoPlayer)
        exoPlayer.stop()
        exoPlayer.seekToDefaultPosition(currentIndex)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    private fun stopAndExit() {
        liveResumeGate.reset()
        currentRadioFallbackState = RadioFallbackState.Inactive
        clearSleepTimerMetadataTracking()
        player?.stop()
        player?.clearMediaItems()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        liveResumeGate.reset()
        sleepTimerPresentationJob?.cancel()
        sleepTimerPresentationJob = null
        sleepTimerPresentationScope.cancel()
        clearSleepTimerMetadataTracking()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        currentRadioFallbackState = RadioFallbackState.Inactive
        super.onDestroy()
    }
}
