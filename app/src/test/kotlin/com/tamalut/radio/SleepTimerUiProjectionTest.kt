package com.tamalut.radio

import com.tamalut.radio.core.playback.SleepTimerCustomDuration
import com.tamalut.radio.core.playback.SleepTimerPreset
import com.tamalut.radio.core.playback.SleepTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerUiProjectionTest {
    @Test fun presetSelectorStillExposesExactlyApprovedQuickOptions() {
        assertEquals(listOf(SleepTimerPreset.OFF, SleepTimerPreset.MINUTES_15, SleepTimerPreset.MINUTES_30, SleepTimerPreset.MINUTES_45, SleepTimerPreset.MINUTES_60), sleepTimerPresetOptions)
        assertEquals(listOf("Off", "15 min", "30 min", "45 min", "60 min"), sleepTimerPresetOptions.map { it.displayLabel() })
    }
    @Test fun activeCountdownProjectionIsSharedAndStable() {
        val model = SleepTimerState(preset = SleepTimerPreset.MINUTES_15, remainingSeconds = 899L).toSleepTimerUiModel()
        assertTrue(model.isActive); assertEquals("Timer 14:59", model.compactLabel); assertEquals("14:59 rimanenti", model.detailLabel)
    }
    @Test fun hourOrLongerCountdownUsesHourMinuteSecondFormat() {
        val state = SleepTimerState(customDurationMinutes = 125, remainingSeconds = 7_500L)
        val model = state.toSleepTimerUiModel()
        assertTrue(state.isCustom); assertTrue(model.isActive); assertEquals("Timer 2:05:00", model.compactLabel)
        assertEquals("2:05:00 rimanenti", model.detailLabel); assertEquals("1:00:00", formatSleepTimerRemaining(3_600L))
    }
    @Test fun customInputValidationAndPreviewUseSharedDurationValue() {
        val duration = sleepTimerCustomDurationOrNull("2", "25"); requireNotNull(duration)
        assertEquals(145, duration.totalMinutes); assertEquals("2 h 25 min", duration.toPreviewLabel())
        assertEquals("1 min", SleepTimerCustomDuration.fromTotalMinutes(1).toPreviewLabel())
        assertEquals("12 h", SleepTimerCustomDuration.fromTotalMinutes(720).toPreviewLabel())
        assertNull(sleepTimerCustomDurationOrNull("0", "0")); assertNull(sleepTimerCustomDurationOrNull("12", "1")); assertNull(sleepTimerCustomDurationOrNull("13", "0")); assertNull(sleepTimerCustomDurationOrNull("", "30"))
    }
    @Test fun offProjectionHasNoCountdown() {
        val model = SleepTimerState().toSleepTimerUiModel(); assertFalse(model.isActive); assertEquals("Timer", model.compactLabel); assertEquals("Off", model.detailLabel)
    }
}
