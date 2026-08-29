package com.tamalut.radio

import android.content.Context
import android.provider.Settings
import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackState
import com.tamalut.radio.core.preferences.UserPreferences
import com.tamalut.radio.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class FloatingOverlayCoordinator(
    context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val playbackController: PlaybackController,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var latestPreferences = UserPreferences()
    private var latestPlaybackState: PlaybackState = playbackController.state.value
    private var sessionState = OverlaySessionState()
    private var appInForeground = true
    private val activityExitGate = OverlayActivityExitGate()

    private val window = FloatingOverlayWindow(
        context = appContext,
        onDismiss = {
            sessionState = sessionState.dismissForCurrentSession()
            reconcile()
        },
        onExpandedChanged = { expanded ->
            sessionState = sessionState.setExpanded(expanded)
            reconcile()
        },
        onPositionChanged = { edge, verticalFraction ->
            latestPreferences = latestPreferences.copy(
                overlayEdge = edge,
                overlayVerticalFraction = verticalFraction,
            )
            scope.launch {
                preferencesRepository.setOverlayPosition(edge, verticalFraction)
            }
        },
        onPlaybackAction = { action ->
            performOverlayPlaybackAction(action, latestPlaybackState, playbackController)
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
        activityExitGate.onAppForeground()
        sessionState = sessionState.endExternalSession()
        window.hide()
    }

    fun onAppStopped(isChangingConfigurations: Boolean) {
        when (activityExitGate.onAppStopped(isChangingConfigurations)) {
            OverlayActivityStopDecision.IGNORE_CONFIGURATION_CHANGE -> return
            OverlayActivityStopDecision.SUPPRESS_EXTERNAL_SESSION -> {
                appInForeground = false
                window.hide()
                return
            }
            OverlayActivityStopDecision.ADMIT_EXTERNAL_SESSION -> {
                appInForeground = false
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

    fun suppressNextAppStop() {
        activityExitGate.suppressNextAppStop()
    }

    private fun reconcile() {
        if (!latestPreferences.overlayEnabled) {
            sessionState = sessionState.endExternalSession()
            window.hide()
            return
        }

        if (sessionState.externalSessionActive && !latestPlaybackState.hasCurrentItem) {
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
            window.hide()
        }
    }

    private fun permissionGranted(): Boolean = Settings.canDrawOverlays(appContext)
}
