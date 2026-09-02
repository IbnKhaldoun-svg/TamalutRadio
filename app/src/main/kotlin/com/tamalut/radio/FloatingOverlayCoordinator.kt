package com.tamalut.radio

import android.content.Context
import android.provider.Settings
import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackLaunchContract
import com.tamalut.radio.core.playback.PlaybackState
import com.tamalut.radio.core.preferences.UserPreferences
import com.tamalut.radio.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class FloatingOverlayCoordinator(
    context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val playbackController: PlaybackController,
    private val onStopRequested: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var latestPreferences = UserPreferences()
    private var latestPlaybackState: PlaybackState = playbackController.state.value
    private var sessionState = OverlaySessionState()
    private var appInForeground = true
    private val activityExitGate = OverlayActivityExitGate()
    private val autoCollapseTimer = OverlayAutoCollapseTimer(
        schedule = { delayMillis, action ->
            val job = scope.launch {
                delay(delayMillis)
                action()
            }
            OverlayScheduledTask { job.cancel() }
        },
        onTimeout = {
            if (sessionState.externalSessionActive && sessionState.expanded && !appInForeground) {
                sessionState = sessionState.setExpanded(false)
                reconcile()
            }
        },
    )

    private val window = FloatingOverlayWindow(
        context = appContext,
        onDismiss = {
            autoCollapseTimer.cancel()
            sessionState = sessionState.dismissForCurrentSession()
            reconcile()
        },
        onExpandedChanged = { expanded ->
            sessionState = sessionState.setExpanded(expanded)
            if (expanded) autoCollapseTimer.arm() else autoCollapseTimer.cancel()
            reconcile()
        },
        onPositionChanged = { edge, verticalFraction ->
            latestPreferences = latestPreferences.copy(
                overlayEdge = edge,
                overlayVerticalFraction = verticalFraction,
            )
            if (sessionState.expanded) autoCollapseTimer.arm()
            scope.launch {
                preferencesRepository.setOverlayPosition(edge, verticalFraction)
            }
        },
        onPlaybackAction = { action ->
            if (sessionState.expanded) autoCollapseTimer.arm()
            performOverlayPlaybackAction(action, latestPlaybackState, playbackController, onStopRequested)
        },
        onOpenApp = {
            autoCollapseTimer.cancel()
            performOverlayAppEntry {
                PlaybackLaunchContract.createNowPlayingPendingIntent(appContext)?.let { pendingIntent ->
                    runCatching { pendingIntent.send() }
                }
            }
        },
        onUserInteraction = {
            if (sessionState.externalSessionActive && sessionState.expanded) autoCollapseTimer.arm()
        },
    )

    init {
        scope.launch {
            preferencesRepository.userPreferences.collect { preferences ->
                latestPreferences = preferences
                reconcile()
            }
        }
        scope.launch {
            playbackController.state.collect { playbackState ->
                latestPlaybackState = playbackState
                if (sessionState.externalSessionActive && !playbackState.hasCurrentItem) {
                    sessionState = sessionState.endExternalSession()
                }
                reconcile()
            }
        }
    }

    fun onAppForeground() {
        appInForeground = true
        autoCollapseTimer.cancel()
        activityExitGate.onAppForeground()
        sessionState = sessionState.endExternalSession()
        window.hide()
    }

    fun onAppStopped(isChangingConfigurations: Boolean) {
        when (activityExitGate.onAppStopped(isChangingConfigurations)) {
            OverlayActivityStopDecision.IGNORE_CONFIGURATION_CHANGE -> return
            OverlayActivityStopDecision.SUPPRESS_EXTERNAL_SESSION -> {
                appInForeground = false
                autoCollapseTimer.cancel()
                window.hide()
                return
            }
            OverlayActivityStopDecision.ADMIT_EXTERNAL_SESSION -> {
                appInForeground = false
                autoCollapseTimer.cancel()
                if (
                    shouldAdmitExternalOverlaySession(
                        overlayEnabled = latestPreferences.overlayEnabled,
                        permissionGranted = permissionGranted(),
                        isPlaying = latestPlaybackState.isPlaying,
                    )
                ) {
                    sessionState = sessionState.beginExternalSession()
                }
                reconcile()
            }
        }
    }

    fun shutdownForSleepTimer() {
        appInForeground = false
        autoCollapseTimer.cancel()
        sessionState = sessionState.endExternalSession()
        window.hide()
    }

    fun release() {
        shutdownForSleepTimer()
        scope.cancel()
    }

    fun suppressNextAppStop() {
        activityExitGate.suppressNextAppStop()
    }

    private fun reconcile() {
        if (!latestPreferences.overlayEnabled) {
            autoCollapseTimer.cancel()
            sessionState = sessionState.endExternalSession()
            window.hide()
            return
        }

        if (sessionState.externalSessionActive && !latestPlaybackState.hasCurrentItem) {
            autoCollapseTimer.cancel()
            sessionState = sessionState.endExternalSession()
        }

        val shouldShow = shouldShowOverlay(
            overlayEnabled = latestPreferences.overlayEnabled,
            permissionGranted = permissionGranted(),
            appInForeground = appInForeground,
            sessionState = sessionState,
            hasCurrentItem = latestPlaybackState.hasCurrentItem,
        )

        if (shouldShow) {
            window.show(
                FloatingOverlayViewState(
                    edge = latestPreferences.overlayEdge,
                    verticalFraction = latestPreferences.overlayVerticalFraction,
                    expanded = sessionState.expanded,
                    playbackControls = latestPlaybackState.toOverlayPlaybackControlsModel(),
                ),
            )
        } else {
            autoCollapseTimer.cancel()
            window.hide()
        }
    }

    private fun permissionGranted(): Boolean = Settings.canDrawOverlays(appContext)
}
