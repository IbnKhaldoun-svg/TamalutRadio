package com.tamalut.radio

import com.tamalut.radio.core.playback.SleepTimerPreset
import com.tamalut.radio.core.playback.SleepTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerUiProjectionTest {
    @Test
    fun presetSelectorExposesExactlyApprovedV1Options() {
        assertEquals(
            listOf(
                SleepTimerPreset.OFF,
                SleepTimerPreset.MINUTES_15,
                SleepTimerPreset.MINUTES_30,
                SleepTimerPreset.MINUTES_45,
                SleepTimerPreset.MINUTES_60,
            ),
            sleepTimerPresetOptions,
        )
        assertEquals(listOf("Off", "15 min", "30 min", "45 min", "60 min"), sleepTimerPresetOptions.map { it.displayLabel() })
    }

    @Test
    fun activeCountdownProjectionIsSharedAndStable() {
        val state = SleepTimerState(
            preset = SleepTimerPreset.MINUTES_15,
            remainingSeconds = 899L,
        )

        val model = state.toSleepTimerUiModel()

        assertTrue(model.isActive)
        assertEquals("Timer 14:59", model.compactLabel)
        assertEquals("14:59 rimanenti", model.detailLabel)
        assertEquals("14:59", formatSleepTimerRemaining(899L))
    }

    @Test
    fun offProjectionHasNoCountdown() {
        val model = SleepTimerState().toSleepTimerUiModel()

        assertFalse(model.isActive)
        assertEquals("Timer", model.compactLabel)
        assertEquals("Off", model.detailLabel)
    }
}
