package com.tamalut.radio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerSettingsOnlyArchitectureTest {
    @Test
    fun settingsIsTheOnlyInAppTimerControlSurface() {
        val main = Path.of("src/main/kotlin/com/tamalut/radio/MainActivity.kt").readText()
        val chrome = Path.of("src/main/kotlin/com/tamalut/radio/PlaybackChrome.kt").readText()
        val settings = main
            .substringAfter("private fun SettingsDestination(")
            .substringBefore("private fun ThemePreference.toThemeMode")
        val mini = chrome
            .substringAfter("fun PersistentMiniPlayer(")
            .substringBefore("@Composable\nfun NowPlayingDestination(")
        val nowPlaying = chrome
            .substringAfter("fun NowPlayingDestination(")
            .substringBefore("@Composable\nfun SleepTimerCustomDialog(")

        assertTrue(settings.contains("Timer spegnimento"))
        assertTrue(settings.contains("sleepTimerPresetOptions.take(3)"))
        assertTrue(settings.contains("sleepTimerPresetOptions.drop(3)"))
        assertTrue(settings.contains("Personalizzato…"))
        assertTrue(settings.contains("sleepTimerModel.detailLabel"))
        assertFalse(mini.contains("Timer spegnimento"))
        assertFalse(mini.contains("sleepTimerPresetOptions"))
        assertFalse(nowPlaying.contains("Timer spegnimento"))
        assertFalse(nowPlaying.contains("sleepTimerPresetOptions"))
    }

    @Test
    fun settingsHourglassIndicatorIsAdjacentReadableAndDrivenBySharedTimerState() {
        val main = Path.of("src/main/kotlin/com/tamalut/radio/MainActivity.kt").readText()
        val nav = main
            .substringAfter("NavigationBar {")
            .substringBefore("}\n                            }\n                        }\n                    },")

        assertTrue(nav.contains("item == MainDestination.SETTINGS && sleepTimerState.isActive"))
        assertTrue(nav.contains("Row("))
        assertTrue(nav.contains("Arrangement.spacedBy(4.dp)"))
        assertTrue(nav.contains("Icons.Filled.HourglassBottom"))
        assertTrue(nav.contains("contentDescription = \"Timer attivo\""))
        assertTrue(nav.contains("Modifier.size(16.dp)"))
        assertFalse(nav.contains("BadgedBox("))
        assertFalse(nav.contains("tint = MaterialTheme.colorScheme.primary"))
        assertFalse(main.lineSequence().any { it.trim() == "import androidx.compose.material3.BadgedBox" })
        assertFalse(nav.contains("remainingSeconds"))
        assertFalse(nav.contains("formatSleepTimerRemaining"))
    }

    @Test
    fun notificationProjectionUsesExistingSessionAndNoParallelPlaybackSurface() {
        val runtime = Path.of("src/main/kotlin/com/tamalut/radio/TamalutRadioRuntime.kt").readText()
        val service = Path.of("..", "core", "playback", "src", "main", "kotlin", "com", "tamalut", "radio", "core", "playback", "TamalutPlaybackService.kt").readText()
        val timer = Path.of("..", "core", "playback", "src", "main", "kotlin", "com", "tamalut", "radio", "core", "playback", "SleepTimer.kt").readText()

        assertTrue(runtime.contains("controller.state.collect"))
        assertTrue(runtime.contains("SleepTimerNotificationBridge.publish(state)"))
        assertTrue(service.contains("SleepTimerNotificationBridge.remainingSeconds.collect"))
        assertTrue(service.contains("sleepTimerNotificationContentText"))
        assertTrue(service.contains("mediaMetadata.buildUpon()"))
        assertTrue(service.contains(".setArtist(contentText)"))
        assertTrue(service.contains("exoPlayer.replaceMediaItem"))
        assertTrue(service.contains("restoreSleepTimerMetadata"))
        assertFalse(service.contains("NotificationManager"))
        assertFalse(service.contains("NotificationChannel"))
        assertFalse(service.contains("startForegroundService"))
        assertTrue(service.split("ExoPlayer.Builder").size - 1 == 1)
        assertTrue(service.split("MediaLibrarySession.Builder").size - 1 == 1)
        assertFalse(timer.contains("SleepTimerNotificationBridge"))
    }
}
