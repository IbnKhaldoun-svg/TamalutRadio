package com.tamalut.radio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingRedesignArchitectureTest {
    @Test
    fun miniPlayerHidesSleepTimerAndTextTapRoutesToNowPlaying() {
        val chrome = Path.of("src/main/kotlin/com/tamalut/radio/PlaybackChrome.kt").readText()
        val main = Path.of("src/main/kotlin/com/tamalut/radio/MainActivity.kt").readText()
        val mini = chrome
            .substringAfter("fun PersistentMiniPlayer(")
            .substringBefore("@Composable\nfun NowPlayingDestination(")

        assertTrue(mini.contains("onOpenNowPlaying: () -> Unit"))
        assertTrue(mini.contains(".clickable(onClick = onOpenNowPlaying)"))
        assertFalse(mini.contains("SleepTimer"))
        assertFalse(mini.contains("Timer"))
        assertFalse(mini.contains("DropdownMenu"))
        assertFalse(mini.contains("compactLabel"))

        assertTrue(main.contains("onOpenNowPlaying"))
        assertTrue(main.contains("selectedDestination.value = MainDestination.NOW_PLAYING"))
    }

    @Test
    fun nowPlayingRemainsScrollableWithoutTimerOrDuplicateTransport() {
        val chrome = Path.of("src/main/kotlin/com/tamalut/radio/PlaybackChrome.kt").readText()
        val nowPlaying = chrome
            .substringAfter("fun NowPlayingDestination(")
            .substringBefore("@Composable\nfun SleepTimerCustomDialog(")

        assertTrue(nowPlaying.contains(".verticalScroll(rememberScrollState())"))
        assertFalse(nowPlaying.contains("horizontalScroll"))
        assertFalse(nowPlaying.contains("Timer spegnimento"))
        assertFalse(nowPlaying.contains("sleepTimerPresetOptions"))
        assertFalse(nowPlaying.contains("Personalizzato…"))
        assertFalse(nowPlaying.contains("SleepTimerState"))
        assertFalse(nowPlaying.contains("SkipPrevious"))
        assertFalse(nowPlaying.contains("SkipNext"))
        assertFalse(nowPlaying.contains("TOGGLE_PLAY_PAUSE"))
    }

    @Test
    fun localMiniPlayerStillDelegatesShuffleAndRepeatThroughSharedController() {
        val chrome = Path.of("src/main/kotlin/com/tamalut/radio/PlaybackChrome.kt").readText()
        val mini = chrome
            .substringAfter("fun PersistentMiniPlayer(")
            .substringBefore("@Composable\nfun NowPlayingDestination(")

        assertTrue(mini.contains("PlaybackChromeAction.TOGGLE_SHUFFLE"))
        assertTrue(mini.contains("PlaybackChromeAction.CYCLE_REPEAT"))
        assertTrue(mini.contains("performPlaybackChromeAction"))
        assertFalse(mini.contains("ExoPlayer.Builder"))
        assertFalse(mini.contains("MediaSession.Builder"))
        assertFalse(mini.contains("MediaBrowser.Builder"))
    }
}
