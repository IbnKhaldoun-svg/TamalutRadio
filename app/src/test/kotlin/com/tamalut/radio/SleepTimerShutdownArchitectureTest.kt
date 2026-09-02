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

        assertTrue(runtime.contains("onExpired = { shutdown(context.applicationContext) }"))
        assertTrue(runtime.contains("fun shutdown(context: Context)"))
        assertTrue(runtime.contains("sleepTimerController?.setPreset(SleepTimerPreset.OFF)"))
        assertTrue(runtime.contains("controller.stopAndExit"))
        assertTrue(controller.contains("sendCustomCommand(PlaybackCommands.stopExitCommand"))
        assertTrue(service.contains("player?.stop()"))
        assertTrue(service.contains("player?.clearMediaItems()"))
        assertTrue(service.contains("pauseAllPlayersAndStopSelf()"))
        assertTrue(runtime.contains("shutdownForSleepTimer()"))
        assertTrue(overlay.contains("window.hide()"))
        assertTrue(runtime.contains("finishAndRemoveTask()"))
        assertTrue(runtime.contains("stopService(Intent(appContext, TamalutPlaybackService::class.java))"))

        val shutdownProduction = runtime + "\n" + controller + "\n" + service
        assertFalse(shutdownProduction.contains("Process.killProcess"))
        assertFalse(shutdownProduction.contains("killProcess("))
        assertFalse(shutdownProduction.contains("exitProcess("))
        assertFalse(runtime.contains("ExoPlayer.Builder"))
        assertFalse(runtime.contains("MediaSession.Builder"))
        assertFalse(runtime.contains("shutdownAfterSleepTimer"))
    }

    @Test
    fun settingsKeepsCustomTimerActionReachableByVerticalScrolling() {
        val main = Path.of("src/main/kotlin/com/tamalut/radio/MainActivity.kt").readText()
        val chrome = Path.of("src/main/kotlin/com/tamalut/radio/PlaybackChrome.kt").readText()
        val settings = main
            .substringAfter("private fun SettingsDestination(")
            .substringBefore("private fun ThemePreference.toThemeMode")
        val nowPlaying = chrome
            .substringAfter("fun NowPlayingDestination(")
            .substringBefore("@Composable\nfun SleepTimerCustomDialog(")

        assertTrue(settings.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(settings.contains("Text(\"Personalizzato…\")"))
        assertTrue(chrome.contains("SleepTimerPreset.MINUTES_60 -> \"60 min\""))
        assertFalse(nowPlaying.contains("Personalizzato…"))
        assertFalse(nowPlaying.contains("sleepTimerPresetOptions"))
    }
}
