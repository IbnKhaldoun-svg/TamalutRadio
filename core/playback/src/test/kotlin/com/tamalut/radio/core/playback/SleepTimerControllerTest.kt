package com.tamalut.radio.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerControllerTest {
    @Test fun presetsExposeExactlyApprovedDurations() {
        assertEquals(listOf(null, 15, 30, 45, 60), SleepTimerPreset.entries.map(SleepTimerPreset::durationMinutes))
    }

    @Test fun customDurationAcceptsOneMinuteAndTwelveHoursAndRejectsOutsideBounds() {
        val minimum = SleepTimerCustomDuration.fromTotalMinutes(1)
        val maximum = SleepTimerCustomDuration.fromTotalMinutes(720)
        assertEquals(0, minimum.hours); assertEquals(1, minimum.minutes)
        assertEquals(12, maximum.hours); assertEquals(0, maximum.minutes)
        assertNull(SleepTimerCustomDuration.fromPartsOrNull(0, 0))
        assertNull(SleepTimerCustomDuration.fromPartsOrNull(12, 1))
        assertNull(SleepTimerCustomDuration.fromPartsOrNull(13, 0))
        assertNull(SleepTimerCustomDuration.fromPartsOrNull(0, 60))
        assertThrows(IllegalArgumentException::class.java) { SleepTimerCustomDuration.fromTotalMinutes(0) }
        assertThrows(IllegalArgumentException::class.java) { SleepTimerCustomDuration.fromTotalMinutes(721) }
    }

    @Test fun customOneMinuteAndTwelveHourTimersUseSameSchedulerPath() {
        val scheduler = FakeSleepTimerScheduler(); val timer = SleepTimerController(scheduler) {}
        timer.setCustomDuration(SleepTimerCustomDuration.fromTotalMinutes(1))
        assertTrue(timer.state.value.isCustom); assertEquals(1, timer.state.value.customDurationMinutes)
        assertEquals(60L, timer.state.value.remainingSeconds); assertEquals(1, scheduler.activeTaskCount())
        timer.setCustomDuration(SleepTimerCustomDuration.fromTotalMinutes(720))
        assertEquals(720, timer.state.value.customDurationMinutes)
        assertEquals(43_200L, timer.state.value.remainingSeconds); assertEquals(1, scheduler.activeTaskCount())
    }

    @Test fun presetToCustomReplacementCancelsOldDeadline() {
        val scheduler = FakeSleepTimerScheduler(); var calls = 0; val timer = SleepTimerController(scheduler) { calls++ }
        timer.setPreset(SleepTimerPreset.MINUTES_15); scheduler.advanceBy(5 * 60_000L)
        timer.setCustomDuration(SleepTimerCustomDuration.fromTotalMinutes(1))
        assertEquals(SleepTimerPreset.OFF, timer.state.value.preset); assertEquals(1, timer.state.value.customDurationMinutes)
        assertEquals(1, scheduler.activeTaskCount()); scheduler.advanceBy(60_000L); assertEquals(1, calls)
    }

    @Test fun customToPresetReplacementCancelsCustomDeadline() {
        val scheduler = FakeSleepTimerScheduler(); var calls = 0; val timer = SleepTimerController(scheduler) { calls++ }
        timer.setCustomDuration(SleepTimerCustomDuration.fromTotalMinutes(1)); scheduler.advanceBy(30_000L)
        timer.setPreset(SleepTimerPreset.MINUTES_30)
        assertEquals(SleepTimerPreset.MINUTES_30, timer.state.value.preset); assertNull(timer.state.value.customDurationMinutes)
        assertEquals(1_800L, timer.state.value.remainingSeconds); assertEquals(1, scheduler.activeTaskCount())
        scheduler.advanceBy(30_000L); assertEquals(0, calls)
    }

    @Test fun customToOffCancelsAndPreventsOldExpiry() {
        val scheduler = FakeSleepTimerScheduler(); var calls = 0; val timer = SleepTimerController(scheduler) { calls++ }
        timer.setCustomDuration(SleepTimerCustomDuration.fromTotalMinutes(1)); timer.setPreset(SleepTimerPreset.OFF)
        assertFalse(timer.state.value.isActive); assertNull(timer.state.value.customDurationMinutes); assertEquals(0, scheduler.activeTaskCount())
        scheduler.advanceBy(60 * 60_000L); assertEquals(0, calls)
    }

    @Test fun customExpiryDelegatesExactlyOnce() {
        val scheduler = FakeSleepTimerScheduler(); var calls = 0; val timer = SleepTimerController(scheduler) { calls++ }
        timer.setCustomDuration(SleepTimerCustomDuration.fromTotalMinutes(1)); scheduler.advanceBy(59_999L)
        assertEquals(0, calls); assertTrue(timer.state.value.isActive); assertEquals(1L, timer.state.value.remainingSeconds)
        scheduler.advanceBy(1L); assertEquals(1, calls); assertFalse(timer.state.value.isActive); assertNull(timer.state.value.customDurationMinutes)
        scheduler.advanceBy(60 * 60_000L); assertEquals(1, calls)
    }

    @Test fun presetCountdownAndReplacementRegressionRemainDeterministic() {
        val scheduler = FakeSleepTimerScheduler(); var calls = 0; val timer = SleepTimerController(scheduler) { calls++ }
        timer.setPreset(SleepTimerPreset.MINUTES_15); scheduler.advanceBy(1_250L); assertEquals(899L, timer.state.value.remainingSeconds)
        timer.setPreset(SleepTimerPreset.MINUTES_30); assertEquals(1_800L, timer.state.value.remainingSeconds); assertEquals(1, scheduler.activeTaskCount())
        scheduler.advanceBy(30 * 60_000L); assertEquals(1, calls)
    }

    private class FakeSleepTimerScheduler : SleepTimerScheduler {
        private data class Task(val runAtMillis: Long, val order: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private var now = 0L; private var order = 0L; private val tasks = mutableListOf<Task>()
        override fun nowMillis(): Long = now
        override fun schedule(delayMillis: Long, action: () -> Unit): SleepTimerHandle {
            val task = Task(now + delayMillis.coerceAtLeast(0L), order++, action); tasks += task
            return SleepTimerHandle { task.cancelled = true }
        }
        fun activeTaskCount() = tasks.count { !it.cancelled }
        fun advanceBy(deltaMillis: Long) {
            require(deltaMillis >= 0); val target = now + deltaMillis
            while (true) {
                val next = tasks.asSequence().filter { !it.cancelled && it.runAtMillis <= target }
                    .minWithOrNull(compareBy<Task> { it.runAtMillis }.thenBy { it.order }) ?: break
                tasks.remove(next); now = next.runAtMillis; next.action()
            }
            now = target
        }
    }
}
