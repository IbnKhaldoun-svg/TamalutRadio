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
        assertTrue(
            coordinator.contains(
                "performOverlayPlaybackAction(action, latestPlaybackState, playbackController)",
            ),
        )
        assertTrue(
            coordinator.contains(
                "playbackControls = latestPlaybackState.toOverlayPlaybackControlsModel()",
            ),
        )
        assertTrue(window.contains("if (state.expanded) transportControls(host, state.playbackControls) else null"))
        assertTrue(window.contains("description = \"Precedente\""))
        assertTrue(window.contains("description = if (model?.playPauseIcon == OverlayPlayPauseIcon.PAUSE) \"Pausa\" else \"Riproduci\""))
        assertTrue(window.contains("description = \"Successivo\""))

        assertFalse(overlayProduction.contains("ExoPlayer.Builder"))
        assertFalse(overlayProduction.contains("MediaBrowser.Builder"))
        assertFalse(overlayProduction.contains("MediaSession.Builder"))
        assertFalse(overlayProduction.contains("MediaSessionService"))
        assertFalse(overlayProduction.contains("startForegroundService"))
    }

    @Test
    fun transportCommandsRemainCapabilityGatedAndIndependentFromOverlaySessionState() {
        val controls = Path.of("src/main/kotlin/com/tamalut/radio/OverlayPlaybackControls.kt").readText()
        val window = Path.of("src/main/kotlin/com/tamalut/radio/FloatingOverlayWindow.kt").readText()

        assertTrue(controls.contains("PREVIOUS -> if (state.canSkipPrevious) controller.skipToPrevious()"))
        assertTrue(controls.contains("TOGGLE_PLAY_PAUSE -> if (state.hasCurrentItem) controller.togglePlayPause()"))
        assertTrue(controls.contains("NEXT -> if (state.canSkipNext) controller.skipToNext()"))

        assertTrue(window.contains("if (isEnabled) onPlaybackAction(action)"))
        assertFalse(controls.contains("OverlaySessionState"))
        assertFalse(controls.contains("onExpandedChanged"))
        assertFalse(controls.contains("onDismiss"))
        assertFalse(controls.contains("setOverlayEnabled"))
    }
}
