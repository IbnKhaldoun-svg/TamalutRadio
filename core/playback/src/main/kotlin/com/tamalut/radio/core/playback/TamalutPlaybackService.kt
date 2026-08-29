package com.tamalut.radio.core.playback

import android.app.PendingIntent
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

@OptIn(UnstableApi::class)
class TamalutPlaybackService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val liveResumeGate = RadioLiveResumeGate()

    var currentRadioFallbackState: RadioFallbackState = RadioFallbackState.Inactive
        private set

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val exoPlayer = player
            val plan = RadioMediaItemFactory.planFrom(mediaItem)
            liveResumeGate.onMediaItemChanged(
                isRadio = plan != null,
                playWhenReady = exoPlayer?.playWhenReady == true,
            )
            currentRadioFallbackState = plan?.asState() ?: RadioFallbackState.Inactive
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
                reconnectCurrentRadioAtLiveEdge(exoPlayer, currentItem, plan)
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
                reconnectCurrentRadioAtLiveEdge(exoPlayer, currentItem, plan)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val exoPlayer = player ?: return
            val plan = RadioMediaItemFactory.planFrom(exoPlayer.currentMediaItem) ?: return

            when (val decision = plan.onFatalError(error.errorCode)) {
                is RadioFallbackDecision.Retry -> {
                    val preservePlayIntent = exoPlayer.playWhenReady
                    currentRadioFallbackState = decision.plan.asState()
                    exoPlayer.setMediaItem(RadioMediaItemFactory.create(decision.plan))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = preservePlayIntent
                }

                is RadioFallbackDecision.Exhausted -> {
                    currentRadioFallbackState = decision.state
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
    }

    private fun reconnectCurrentRadioAtLiveEdge(
        exoPlayer: ExoPlayer,
        currentItem: MediaItem,
        plan: RadioFallbackPlan,
    ) {
        currentRadioFallbackState = plan.asState()
        exoPlayer.stop()
        exoPlayer.setMediaItem(currentItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    private fun stopAndExit() {
        liveResumeGate.reset()
        currentRadioFallbackState = RadioFallbackState.Inactive
        player?.stop()
        player?.clearMediaItems()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        liveResumeGate.reset()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        currentRadioFallbackState = RadioFallbackState.Inactive
        super.onDestroy()
    }
}
