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
    fun stateProjectionDescribesCurrentSourceAndTransportCapabilities() {
        val radio = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.RADIO,
            stationId = StationId("azawan"),
            title = "Radio Azawan",
            isPlaying = true,
            canSkipNext = true,
        ).toPlaybackChromeModel()

        requireNotNull(radio)
        assertEquals("Radio Azawan", radio.title)
        assertEquals("Radio · LIVE", radio.sourceLabel)
        assertTrue(radio.isPlaying)
        assertFalse(radio.canSkipPrevious)
        assertTrue(radio.canSkipNext)

        val local = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.LOCAL,
            mediaId = MediaId("track"),
            title = "Track",
        ).toPlaybackChromeModel()
        requireNotNull(local)
        assertEquals("Musica locale", local.sourceLabel)
        assertFalse(local.isPlaying)

        assertNull(PlaybackState(isConnected = true).toPlaybackChromeModel())
    }

    @Test
    fun chromeActionsDelegateToTheSharedPlaybackController() {
        val controller = FakePlaybackController()

        performPlaybackChromeAction(PlaybackChromeAction.PREVIOUS, controller)
        performPlaybackChromeAction(PlaybackChromeAction.TOGGLE_PLAY_PAUSE, controller)
        performPlaybackChromeAction(PlaybackChromeAction.NEXT, controller)

        assertEquals(1, controller.previousCalls)
        assertEquals(1, controller.toggleCalls)
        assertEquals(1, controller.nextCalls)
    }

    private class FakePlaybackController : PlaybackController {
        private val current = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = current.asStateFlow()
        var previousCalls = 0
        var toggleCalls = 0
        var nextCalls = 0

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
        override fun setLocalRepeatMode(mode: PlaybackRepeatMode) = Unit
        override fun setLocalShuffleEnabled(enabled: Boolean) = Unit
        override fun release() = Unit
    }
}
