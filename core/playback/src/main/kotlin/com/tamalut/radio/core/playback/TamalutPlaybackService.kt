package com.tamalut.radio.core.playback

import android.app.PendingIntent
import android.content.Intent
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
        createSessionActivityPendingIntent()?.let { pendingIntent ->
            sessionBuilder.setSessionActivity(pendingIntent)
        }
        mediaLibrarySession = sessionBuilder.build()
    }

    private fun createSessionActivityPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.action = PlaybackLaunchContract.ACTION_OPEN_NOW_PLAYING
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            PlaybackLaunchContract.REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
