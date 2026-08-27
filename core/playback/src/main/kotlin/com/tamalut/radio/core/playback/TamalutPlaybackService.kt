package com.tamalut.radio.core.playback

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

    var currentRadioFallbackState: RadioFallbackState = RadioFallbackState.Inactive
        private set

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentRadioFallbackState = RadioMediaItemFactory.planFrom(mediaItem)?.asState()
                ?: RadioFallbackState.Inactive
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
                    // Deliberately do not clear/re-prepare here. The final fatal Media3
                    // PlaybackException remains visible to MediaSession/controllers.
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
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            PlaybackSessionCallback(::stopAndExit),
        ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    private fun stopAndExit() {
        currentRadioFallbackState = RadioFallbackState.Inactive
        player?.stop()
        player?.clearMediaItems()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        currentRadioFallbackState = RadioFallbackState.Inactive
        super.onDestroy()
    }
}
