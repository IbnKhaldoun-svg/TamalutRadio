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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlaybackControlsTest {
    @Test
    fun transportActionsDelegateToTheSharedController() {
        val controller = FakePlaybackController()
        val state = localState(isPlaying = true, canPrevious = true, canNext = true)

        performOverlayPlaybackAction(OverlayPlaybackAction.PREVIOUS, state, controller)
        performOverlayPlaybackAction(OverlayPlaybackAction.TOGGLE_PLAY_PAUSE, state, controller)
        performOverlayPlaybackAction(OverlayPlaybackAction.NEXT, state, controller)

        assertEquals(1, controller.previousCalls)
        assertEquals(1, controller.toggleCalls)
        assertEquals(1, controller.nextCalls)
    }

    @Test
    fun stopDelegatesExactlyOnceToShutdownCallbackWithoutTransportMutation() {
        val controller = FakePlaybackController()
        val state = localState(isPlaying = true, canPrevious = true, canNext = true)
        var stopCalls = 0

        performOverlayPlaybackAction(
            OverlayPlaybackAction.STOP,
            state,
            controller,
            onStop = { stopCalls += 1 },
        )

        assertEquals(1, stopCalls)
        assertEquals(0, controller.previousCalls)
        assertEquals(0, controller.toggleCalls)
        assertEquals(0, controller.nextCalls)
        assertTrue(controller.repeatValues.isEmpty())
        assertTrue(controller.shuffleValues.isEmpty())
    }

    @Test
    fun sharedStateChangesUpdatePlayPausePresentation() {
        val controller = FakePlaybackController()
        controller.publish(localState(isPlaying = true, canPrevious = true, canNext = true))
        assertEquals(
            OverlayPlayPauseIcon.PAUSE,
            controller.state.value.toOverlayPlaybackControlsModel()?.playPauseIcon,
        )

        controller.publish(localState(isPlaying = false, canPrevious = true, canNext = true))
        assertEquals(
            OverlayPlayPauseIcon.PLAY,
            controller.state.value.toOverlayPlaybackControlsModel()?.playPauseIcon,
        )
    }

    @Test
    fun previousAndNextAreGatedByRealPlayerCapabilities() {
        val controller = FakePlaybackController()
        val state = localState(isPlaying = true, canPrevious = false, canNext = false)
        val model = state.toOverlayPlaybackControlsModel()
        requireNotNull(model)
        assertFalse(model.previousEnabled)
        assertFalse(model.nextEnabled)

        performOverlayPlaybackAction(OverlayPlaybackAction.PREVIOUS, state, controller)
        performOverlayPlaybackAction(OverlayPlaybackAction.NEXT, state, controller)

        assertEquals(0, controller.previousCalls)
        assertEquals(0, controller.nextCalls)
    }

    @Test
    fun radioRegressionUsesSameStateAndControllerWithoutLocalModeMutation() {
        val controller = FakePlaybackController()
        val radio = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.RADIO,
            stationId = StationId("radio"),
            title = "Radio",
            isPlaying = true,
            canSkipPrevious = false,
            canSkipNext = false,
        )
        val model = radio.toOverlayPlaybackControlsModel()
        assertNotNull(model)
        assertEquals(OverlayPlayPauseIcon.PAUSE, model?.playPauseIcon)
        assertFalse(model?.previousEnabled ?: true)
        assertFalse(model?.nextEnabled ?: true)

        performOverlayPlaybackAction(OverlayPlaybackAction.PREVIOUS, radio, controller)
        performOverlayPlaybackAction(OverlayPlaybackAction.TOGGLE_PLAY_PAUSE, radio, controller)
        performOverlayPlaybackAction(OverlayPlaybackAction.NEXT, radio, controller)

        assertEquals(0, controller.previousCalls)
        assertEquals(1, controller.toggleCalls)
        assertEquals(0, controller.nextCalls)
        assertTrue(controller.repeatValues.isEmpty())
        assertTrue(controller.shuffleValues.isEmpty())
    }

    @Test
    fun localMusicRegressionPreservesQueueCapabilitiesAndModes() {
        val controller = FakePlaybackController()
        val local = localState(isPlaying = false, canPrevious = true, canNext = true).copy(
            repeatMode = PlaybackRepeatMode.ONE,
            shuffleEnabled = true,
        )
        val model = local.toOverlayPlaybackControlsModel()
        requireNotNull(model)
        assertEquals(OverlayPlayPauseIcon.PLAY, model.playPauseIcon)
        assertTrue(model.previousEnabled)
        assertTrue(model.nextEnabled)

        performOverlayPlaybackAction(OverlayPlaybackAction.PREVIOUS, local, controller)
        performOverlayPlaybackAction(OverlayPlaybackAction.NEXT, local, controller)

        assertEquals(1, controller.previousCalls)
        assertEquals(1, controller.nextCalls)
        assertTrue(controller.repeatValues.isEmpty())
        assertTrue(controller.shuffleValues.isEmpty())
    }

    @Test
    fun noCurrentItemProducesNoControlsAndNoPlayPauseCommand() {
        val controller = FakePlaybackController()
        val empty = PlaybackState(isConnected = true)
        assertNull(empty.toOverlayPlaybackControlsModel())
        var stopCalls = 0
        performOverlayPlaybackAction(OverlayPlaybackAction.TOGGLE_PLAY_PAUSE, empty, controller)
        performOverlayPlaybackAction(
            OverlayPlaybackAction.STOP,
            empty,
            controller,
            onStop = { stopCalls += 1 },
        )
        assertEquals(0, controller.toggleCalls)
        assertEquals(0, stopCalls)
    }

    private fun localState(
        isPlaying: Boolean,
        canPrevious: Boolean,
        canNext: Boolean,
    ): PlaybackState = PlaybackState(
        isConnected = true,
        sourceType = MediaSourceType.LOCAL,
        mediaId = MediaId("track"),
        title = "Track",
        isPlaying = isPlaying,
        canSkipPrevious = canPrevious,
        canSkipNext = canNext,
        repeatMode = PlaybackRepeatMode.ALL,
    )

    private class FakePlaybackController : PlaybackController {
        private val current = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = current.asStateFlow()
        var previousCalls = 0
        var toggleCalls = 0
        var nextCalls = 0
        val repeatValues = mutableListOf<PlaybackRepeatMode>()
        val shuffleValues = mutableListOf<Boolean>()

        fun publish(state: PlaybackState) {
            current.value = state
        }

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
