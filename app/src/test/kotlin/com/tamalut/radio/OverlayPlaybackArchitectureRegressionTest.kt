package com.tamalut.radio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlaybackArchitectureRegressionTest {
    @Test
    fun overlayUsesOnlyTheSharedPlaybackControllerAndState() {
        val coordinator = Path.of("src/main/kotlin/com/tamalut/radio/FloatingOverlayCoordinator.kt").readText()
        val window = Path.of("src/main/kotlin/com/tamalut/radio/FloatingOverlayWindow.kt").readText()
        val controls = Path.of("src/main/kotlin/com/tamalut/radio/OverlayPlaybackControls.kt").readText()
        val overlayProduction = coordinator + "\n" + window + "\n" + controls

        assertTrue(coordinator.contains("playbackController.state.collect"))
        assertTrue(coordinator.contains("private val onStopRequested: () -> Unit"))
        assertTrue(
            coordinator.contains(
                "performOverlayPlaybackAction(action, latestPlaybackState, playbackController, onStopRequested)",
            ),
        )
        assertTrue(
            coordinator.contains(
                "playbackControls = latestPlaybackState.toOverlayPlaybackControlsModel()",
            ),
        )
        assertTrue(controls.contains("title = title?.trim()?.takeIf(String::isNotEmpty) ?: \"In riproduzione\""))
        assertTrue(window.contains("if (state.expanded)"))
        assertTrue(window.contains("playbackTitle(host, state.playbackControls?.title)"))
        assertTrue(window.contains("expandedControlsRow(host, state)"))
        assertTrue(window.contains("maxLines = 1"))
        assertTrue(window.contains("ellipsize = TextUtils.TruncateAt.END"))
        assertTrue(window.contains("description = \"Precedente\""))
        assertTrue(window.contains("description = if (model?.playPauseIcon == OverlayPlayPauseIcon.PAUSE) \"Pausa\" else \"Riproduci\""))
        assertTrue(window.contains("description = \"Successivo\""))
        assertTrue(window.contains("contentDescription = \"Stop\""))

        assertFalse(overlayProduction.contains("ExoPlayer.Builder"))
        assertFalse(overlayProduction.contains("MediaBrowser.Builder"))
        assertFalse(overlayProduction.contains("MediaSession.Builder"))
        assertFalse(overlayProduction.contains("MediaSessionService"))
        assertFalse(overlayProduction.contains("startForegroundService"))
    }

    @Test
    fun titleIsOnlyInExpandedStructureAndGeometryUsesRenderedHeight() {
        val window = Path.of("src/main/kotlin/com/tamalut/radio/FloatingOverlayWindow.kt").readText()

        val renderBody = window.substringAfter("private fun render(host: OverlayWindowHost, state: FloatingOverlayViewState)")
            .substringBefore("private fun expandedControlsRow")
        assertTrue(renderBody.contains("val height = if (state.expanded) host.expandedHeight else host.controlHeight"))
        assertTrue(renderBody.contains("if (state.expanded)"))
        assertTrue(renderBody.contains("playbackTitle(host, state.playbackControls?.title)"))
        assertTrue(renderBody.contains("edgeTab(host, state)"))
        assertTrue(window.contains("windowHeight = host.params.height"))
        assertTrue(window.contains("val titleHeight = dp(24)"))
        assertTrue(window.contains("val expandedHeight = controlHeight + titleHeight"))
    }

    @Test
    fun transportCommandsRemainCapabilityGatedAndIndependentFromOverlaySessionState() {
        val controls = Path.of("src/main/kotlin/com/tamalut/radio/OverlayPlaybackControls.kt").readText()
        val window = Path.of("src/main/kotlin/com/tamalut/radio/FloatingOverlayWindow.kt").readText()

        assertTrue(controls.contains("PREVIOUS -> if (state.canSkipPrevious) controller.skipToPrevious()"))
        assertTrue(controls.contains("TOGGLE_PLAY_PAUSE -> if (state.hasCurrentItem) controller.togglePlayPause()"))
        assertTrue(controls.contains("NEXT -> if (state.canSkipNext) controller.skipToNext()"))
        assertTrue(controls.contains("STOP -> if (state.hasCurrentItem) onStop()"))

        assertTrue(window.contains("if (isEnabled) onPlaybackAction(action)"))
        assertTrue(window.contains("if (isEnabled) onPlaybackAction(OverlayPlaybackAction.STOP)"))
        assertFalse(controls.contains("OverlaySessionState"))
        assertFalse(controls.contains("onExpandedChanged"))
        assertFalse(controls.contains("onDismiss"))
        assertFalse(controls.contains("setOverlayEnabled"))
    }
}
