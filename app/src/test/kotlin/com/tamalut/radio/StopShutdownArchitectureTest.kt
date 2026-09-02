package com.tamalut.radio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopShutdownArchitectureTest {
    @Test
    fun sleepTimerExpiryMiniPlayerAndOverlayShareOneRuntimeShutdownEntryPoint() {
        val runtime = Path.of("src/main/kotlin/com/tamalut/radio/TamalutRadioRuntime.kt").readText()
        val main = Path.of("src/main/kotlin/com/tamalut/radio/MainActivity.kt").readText()
        val coordinator = Path.of("src/main/kotlin/com/tamalut/radio/FloatingOverlayCoordinator.kt").readText()

        assertTrue(runtime.contains("onExpired = { shutdown(context.applicationContext) }"))
        assertTrue(runtime.contains("fun shutdown(context: Context)"))
        assertTrue(runtime.contains("sleepTimerController?.setPreset(SleepTimerPreset.OFF)"))
        assertTrue(runtime.contains("controller.stopAndExit"))
        assertTrue(runtime.contains("coordinator?.shutdownForSleepTimer()"))
        assertTrue(runtime.contains("coordinator?.release()"))
        assertTrue(runtime.contains("controller.release()"))
        assertTrue(runtime.contains("stopService(Intent(appContext, TamalutPlaybackService::class.java))"))
        assertTrue(runtime.contains("task.finishAndRemoveTask()"))
        assertTrue(main.contains("onStop = { TamalutRadioRuntime.shutdown(applicationContext) }"))
        assertTrue(runtime.contains("onStopRequested = { shutdown(context.applicationContext) }"))
        assertTrue(coordinator.contains("onStopRequested: () -> Unit"))
        assertTrue(coordinator.contains("performOverlayPlaybackAction(action, latestPlaybackState, playbackController, onStopRequested)"))
        assertFalse(runtime.contains("shutdownAfterSleepTimer"))
    }

    @Test
    fun miniPlayerAndExpandedOverlayExposeStopAsFourthTransportWithoutNewArchitecture() {
        val chrome = Path.of("src/main/kotlin/com/tamalut/radio/PlaybackChrome.kt").readText()
        val window = Path.of("src/main/kotlin/com/tamalut/radio/FloatingOverlayWindow.kt").readText()
        val overlayControls = Path.of("src/main/kotlin/com/tamalut/radio/OverlayPlaybackControls.kt").readText()
        val mini = chrome.substringAfter("fun PersistentMiniPlayer(").substringBefore("@Composable\nfun NowPlayingDestination(")

        assertTrue(chrome.contains("import androidx.compose.material.icons.filled.Stop"))
        assertTrue(mini.contains("PlaybackChromeAction.NEXT"))
        assertTrue(mini.contains("PlaybackChromeAction.STOP"))
        assertTrue(mini.indexOf("PlaybackChromeAction.STOP") > mini.indexOf("PlaybackChromeAction.NEXT"))
        assertTrue(mini.contains("Icon(Icons.Filled.Stop, contentDescription = \"Stop\")"))
        assertTrue(overlayControls.contains("NEXT,\n    STOP,"))
        assertTrue(window.contains("transportButtonWidth * 4"))
        assertTrue(window.contains("onPlaybackAction(OverlayPlaybackAction.STOP)"))
        assertTrue(window.contains("contentDescription = \"Stop\""))
        assertTrue(window.contains("text = \"■\""))

        val combined = runtimeArchitectureSources()
        assertFalse(combined.contains("ExoPlayer.Builder"))
        assertFalse(combined.contains("MediaSession.Builder"))
        assertFalse(combined.contains("MediaBrowser.Builder"))
        assertFalse(combined.contains("WorkManager"))
    }

    private fun runtimeArchitectureSources(): String = listOf(
        "TamalutRadioRuntime.kt",
        "MainActivity.kt",
        "PlaybackChrome.kt",
        "FloatingOverlayCoordinator.kt",
        "FloatingOverlayWindow.kt",
        "OverlayPlaybackControls.kt",
    ).joinToString("\n") { file ->
        Path.of("src/main/kotlin/com/tamalut/radio/$file").readText()
    }
}
