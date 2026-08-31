package com.tamalut.radio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerShutdownArchitectureTest {
    @Test
    fun expiryReusesSharedStopExitAndRemovesTheAppTask() {
        val runtime = Path.of("src/main/kotlin/com/tamalut/radio/TamalutRadioRuntime.kt").readText()
        val overlay = Path.of("src/main/kotlin/com/tamalut/radio/FloatingOverlayCoordinator.kt").readText()
        val controller = Path.of("../core/playback/src/main/kotlin/com/tamalut/radio/core/playback/SharedPlaybackController.kt").readText()
        val service = Path.of("../core/playback/src/main/kotlin/com/tamalut/radio/core/playback/TamalutPlaybackService.kt").readText()

        assertTrue(runtime.contains("controller.stopAndExit"))
        assertTrue(controller.contains("sendCustomCommand(PlaybackCommands.stopExitCommand"))
        assertTrue(service.contains("player?.stop()"))
        assertTrue(service.contains("player?.clearMediaItems()"))
        assertTrue(service.contains("pauseAllPlayersAndStopSelf()"))
        assertTrue(runtime.contains("shutdownForSleepTimer()"))
        assertTrue(overlay.contains("window.hide()"))
        assertTrue(runtime.contains("finishAndRemoveTask()"))
        assertTrue(runtime.contains("stopService(Intent(context, TamalutPlaybackService::class.java))"))

        val shutdownProduction = runtime + "\n" + controller + "\n" + service
        assertFalse(shutdownProduction.contains("Process.killProcess"))
        assertFalse(shutdownProduction.contains("killProcess("))
        assertFalse(shutdownProduction.contains("exitProcess("))
        assertFalse(runtime.contains("ExoPlayer.Builder"))
        assertFalse(runtime.contains("MediaSession.Builder"))
    }

    @Test
    fun nowPlayingKeepsCustomTimerActionReachableByVerticalScrolling() {
        val chrome = Path.of("src/main/kotlin/com/tamalut/radio/PlaybackChrome.kt").readText()

        assertTrue(chrome.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(chrome.contains("Text(\"Personalizzato…\")"))
        assertTrue(chrome.contains("SleepTimerPreset.MINUTES_60 -> \"60 min\""))
    }
}
