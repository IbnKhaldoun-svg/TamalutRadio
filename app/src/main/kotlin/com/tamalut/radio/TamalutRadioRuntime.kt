package com.tamalut.radio

import android.content.Context
import com.tamalut.radio.core.playback.Media3PlaybackController
import com.tamalut.radio.core.preferences.DataStoreUserPreferencesRepository

internal object TamalutRadioRuntime {
    private var preferencesRepository: DataStoreUserPreferencesRepository? = null
    private var playbackController: Media3PlaybackController? = null
    private var overlayCoordinator: FloatingOverlayCoordinator? = null

    @Synchronized
    fun preferences(context: Context): DataStoreUserPreferencesRepository =
        preferencesRepository ?: DataStoreUserPreferencesRepository(context.applicationContext).also {
            preferencesRepository = it
        }

    @Synchronized
    fun playback(context: Context): Media3PlaybackController =
        playbackController ?: Media3PlaybackController(context.applicationContext).also {
            playbackController = it
        }

    @Synchronized
    fun overlay(context: Context): FloatingOverlayCoordinator =
        overlayCoordinator ?: FloatingOverlayCoordinator(
            context = context.applicationContext,
            preferencesRepository = preferences(context),
            playbackController = playback(context),
        ).also {
            overlayCoordinator = it
        }
}
