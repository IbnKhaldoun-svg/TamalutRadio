package com.tamalut.radio.core.playback

import androidx.media3.common.PlaybackException
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioFallbackPlanTest {
    private val station = RadioStation(
        id = StationId("test-station"),
        name = "Test Station",
        primaryStream = StreamEndpoint("https://example.test/primary.mp3"),
        fallbackStreams = listOf(
            StreamEndpoint("https://example.test/fallback-1.mp3"),
            StreamEndpoint("https://example.test/fallback-2.mp3"),
        ),
    )

    @Test
    fun fatalConnectionErrorsAdvancePrimaryThenFallbacksInOrder() {
        val initial = RadioFallbackPlan.fromStation(station, RadioFallbackConfig(maxAttempts = 3))
        assertEquals("https://example.test/primary.mp3", initial.currentEndpoint.url)
        assertEquals(1, initial.attemptNumber)

        val firstFailure = initial.onFatalError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertTrue(firstFailure is RadioFallbackDecision.Retry)
        val fallbackOne = (firstFailure as RadioFallbackDecision.Retry).plan
        assertEquals("https://example.test/fallback-1.mp3", fallbackOne.currentEndpoint.url)
        assertEquals(2, fallbackOne.attemptNumber)

        val secondFailure = fallbackOne.onFatalError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        assertTrue(secondFailure is RadioFallbackDecision.Retry)
        val fallbackTwo = (secondFailure as RadioFallbackDecision.Retry).plan
        assertEquals("https://example.test/fallback-2.mp3", fallbackTwo.currentEndpoint.url)
        assertEquals(3, fallbackTwo.attemptNumber)

        val finalFailure = fallbackTwo.onFatalError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertTrue(finalFailure is RadioFallbackDecision.Exhausted)
        val exhausted = (finalFailure as RadioFallbackDecision.Exhausted).state
        assertEquals(StationId("test-station"), exhausted.stationId)
        assertEquals(3, exhausted.attemptedCount)
        assertEquals(3, exhausted.maxAttempts)
        assertEquals(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, exhausted.lastErrorCode)
    }

    @Test
    fun maxAttemptsTruncatesFallbackSequenceWithoutCycling() {
        val initial = RadioFallbackPlan.fromStation(station, RadioFallbackConfig(maxAttempts = 2))
        val firstFailure = initial.onFatalError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertTrue(firstFailure is RadioFallbackDecision.Retry)
        val fallbackOne = (firstFailure as RadioFallbackDecision.Retry).plan
        assertEquals("https://example.test/fallback-1.mp3", fallbackOne.currentEndpoint.url)

        val secondFailure = fallbackOne.onFatalError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertTrue(secondFailure is RadioFallbackDecision.Exhausted)
        val exhausted = (secondFailure as RadioFallbackDecision.Exhausted).state
        assertEquals(2, exhausted.attemptedCount)
        assertEquals(2, exhausted.maxAttempts)
    }

    @Test
    fun attemptBudgetIsCappedByAvailableEndpoints() {
        val plan = RadioFallbackPlan.fromStation(station, RadioFallbackConfig(maxAttempts = 99))
        assertEquals(3, plan.maxAttempts)
    }

    @Test
    fun lastEndpointFailureAlwaysReturnsExplicitExhaustedState() {
        val onlyPrimary = station.copy(fallbackStreams = emptyList())
        val plan = RadioFallbackPlan.fromStation(onlyPrimary, RadioFallbackConfig(maxAttempts = 3))
        val result = plan.onFatalError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertTrue(result is RadioFallbackDecision.Exhausted)
        val exhausted = (result as RadioFallbackDecision.Exhausted).state
        assertEquals(1, exhausted.attemptedCount)
        assertEquals(1, exhausted.maxAttempts)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroMaxAttemptsIsRejected() {
        RadioFallbackConfig(maxAttempts = 0)
    }
}
