package com.tamalut.radio

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.playback.LocalPlaybackItem
import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackRepeatMode
import com.tamalut.radio.core.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackChromeTest {
    @Test
    fun stateProjectionDescribesSourceTransportAndLocalModeVisibility() {
        val radio = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.RADIO,
            stationId = StationId("azawan"),
            title = "Radio Azawan",
            isPlaying = true,
            canSkipNext = true,
            repeatMode = PlaybackRepeatMode.ALL,
            shuffleEnabled = true,
        ).toPlaybackChromeModel()

        requireNotNull(radio)
        assertEquals("Radio Azawan", radio.title)
        assertEquals("Radio · LIVE", radio.sourceLabel)
        assertTrue(radio.isPlaying)
        assertFalse(radio.canSkipPrevious)
        assertTrue(radio.canSkipNext)
        assertFalse(radio.showLocalPlaybackModes)
        assertEquals(PlaybackRepeatMode.OFF, radio.repeatMode)
        assertFalse(radio.shuffleEnabled)

        val local = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.LOCAL,
            mediaId = MediaId("track"),
            title = "Track",
            repeatMode = PlaybackRepeatMode.ALL,
            shuffleEnabled = true,
        ).toPlaybackChromeModel()
        requireNotNull(local)
        assertEquals("Musica locale", local.sourceLabel)
        assertFalse(local.isPlaying)
        assertTrue(local.showLocalPlaybackModes)
        assertEquals(PlaybackRepeatMode.ALL, local.repeatMode)
        assertTrue(local.shuffleEnabled)

        assertNull(PlaybackState(isConnected = true).toPlaybackChromeModel())
    }

    @Test
    fun transportActionsDelegateToTheSharedPlaybackController() {
        val controller = FakePlaybackController()
        val state = PlaybackState()

        performPlaybackChromeAction(PlaybackChromeAction.PREVIOUS, state, controller)
        performPlaybackChromeAction(PlaybackChromeAction.TOGGLE_PLAY_PAUSE, state, controller)
        performPlaybackChromeAction(PlaybackChromeAction.NEXT, state, controller)

        assertEquals(1, controller.previousCalls)
        assertEquals(1, controller.toggleCalls)
        assertEquals(1, controller.nextCalls)
    }

    @Test
    fun localModeActionsDelegateAndRadioRejectsThem() {
        val controller = FakePlaybackController()
        val localState = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.LOCAL,
            mediaId = MediaId("track"),
            repeatMode = PlaybackRepeatMode.ALL,
            shuffleEnabled = false,
        )

        performPlaybackChromeAction(PlaybackChromeAction.TOGGLE_SHUFFLE, localState, controller)
        performPlaybackChromeAction(PlaybackChromeAction.CYCLE_REPEAT, localState, controller)

        assertEquals(listOf(true), controller.shuffleValues)
        assertEquals(listOf(PlaybackRepeatMode.ONE), controller.repeatValues)

        val radioState = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.RADIO,
            stationId = StationId("radio"),
            repeatMode = PlaybackRepeatMode.OFF,
            shuffleEnabled = false,
        )
        performPlaybackChromeAction(PlaybackChromeAction.TOGGLE_SHUFFLE, radioState, controller)
        performPlaybackChromeAction(PlaybackChromeAction.CYCLE_REPEAT, radioState, controller)

        assertEquals(listOf(true), controller.shuffleValues)
        assertEquals(listOf(PlaybackRepeatMode.ONE), controller.repeatValues)
    }

    private class FakePlaybackController : PlaybackController {
        private val current = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = current.asStateFlow()
        var previousCalls = 0
        var toggleCalls = 0
        var nextCalls = 0
        val repeatValues = mutableListOf<PlaybackRepeatMode>()
        val shuffleValues = mutableListOf<Boolean>()

        override fun playRadio(station: RadioStation, onResult: (Result<Unit>) -> Unit) {
            onResult(Result.success(Unit))
        }

        override fun playLocal(
            items: List<LocalPlaybackItem>,
            startIndex: Int,
            onResult: (Result<Unit>) -> Unit,
        ) {
            onResult(Result.success(Unit))
        }

        override fun togglePlayPause() { toggleCalls += 1 }
        override fun skipToPrevious() { previousCalls += 1 }
        override fun skipToNext() { nextCalls += 1 }
        override fun setLocalRepeatMode(mode: PlaybackRepeatMode) { repeatValues += mode }
        override fun setLocalShuffleEnabled(enabled: Boolean) { shuffleValues += enabled }
        override fun release() = Unit
    }
}
