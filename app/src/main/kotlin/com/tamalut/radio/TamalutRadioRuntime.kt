package com.tamalut.radio

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.tamalut.radio.core.playback.HandlerSleepTimerScheduler
import com.tamalut.radio.core.playback.Media3PlaybackController
import com.tamalut.radio.core.playback.SleepTimerController
import com.tamalut.radio.core.playback.SleepTimerNotificationBridge
import com.tamalut.radio.core.playback.TamalutPlaybackService
import com.tamalut.radio.core.preferences.DataStoreUserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal object TamalutRadioRuntime {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sleepTimerPresentationJob: Job? = null
    private var preferencesRepository: DataStoreUserPreferencesRepository? = null
    private var playbackController: Media3PlaybackController? = null
    private var overlayCoordinator: FloatingOverlayCoordinator? = null
    private var sleepTimerController: SleepTimerController? = null

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
    fun sleepTimer(context: Context): SleepTimerController =
        sleepTimerController ?: SleepTimerController(
            scheduler = HandlerSleepTimerScheduler(),
            onExpired = { shutdownAfterSleepTimer(context.applicationContext) },
        ).also { controller ->
            sleepTimerController = controller
            sleepTimerPresentationJob?.cancel()
            sleepTimerPresentationJob = runtimeScope.launch {
                controller.state.collect { state ->
                    SleepTimerNotificationBridge.publish(state)
                }
            }
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

    private fun shutdownAfterSleepTimer(context: Context) {
        val controller = playback(context)
        controller.stopAndExit {
            val coordinator = synchronized(this) { overlayCoordinator }
            coordinator?.shutdownForSleepTimer()
            coordinator?.release()
            controller.release()
            synchronized(this) {
                if (overlayCoordinator === coordinator) overlayCoordinator = null
                if (playbackController === controller) playbackController = null
            }
            context.stopService(Intent(context, TamalutPlaybackService::class.java))
            context.getSystemService(ActivityManager::class.java)
                ?.appTasks
                ?.forEach { task -> runCatching { task.finishAndRemoveTask() } }
        }
    }
}
