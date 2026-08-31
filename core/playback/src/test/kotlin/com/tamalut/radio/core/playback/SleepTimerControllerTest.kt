package com.tamalut.radio.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerControllerTest {
    @Test
    fun presetsExposeExactlyApprovedDurations() {
        assertEquals(
            listOf(null, 15, 30, 45, 60),
            SleepTimerPreset.entries.map(SleepTimerPreset::durationMinutes),
        )
    }

    @Test
    fun countdownUsesFakeTimeWithoutRealWaiting() {
        val scheduler = FakeSleepTimerScheduler()
        val timer = SleepTimerController(scheduler, onExpired = {})

        timer.setPreset(SleepTimerPreset.MINUTES_15)
        assertTrue(timer.state.value.isActive)
        assertEquals(900L, timer.state.value.remainingSeconds)

        scheduler.advanceBy(1_250L)
        assertEquals(899L, timer.state.value.remainingSeconds)
    }

    @Test
    fun offCancelsActiveTimerAndPreventsOldExpiry() {
        val scheduler = FakeSleepTimerScheduler()
        var expiryCalls = 0
        val timer = SleepTimerController(scheduler) { expiryCalls += 1 }

        timer.setPreset(SleepTimerPreset.MINUTES_15)
        timer.setPreset(SleepTimerPreset.OFF)

        assertFalse(timer.state.value.isActive)
        assertEquals(SleepTimerPreset.OFF, timer.state.value.preset)
        assertEquals(0L, timer.state.value.remainingSeconds)
        assertEquals(0, scheduler.activeTaskCount())

        scheduler.advanceBy(60L * 60_000L)
        assertEquals(0, expiryCalls)
    }

    @Test
    fun replacingTimerCancelsOldDeadlineAndKeepsOnlyOneCallback() {
        val scheduler = FakeSleepTimerScheduler()
        var expiryCalls = 0
        val timer = SleepTimerController(scheduler) { expiryCalls += 1 }

        timer.setPreset(SleepTimerPreset.MINUTES_15)
        scheduler.advanceBy(5L * 60_000L)
        timer.setPreset(SleepTimerPreset.MINUTES_30)

        assertEquals(SleepTimerPreset.MINUTES_30, timer.state.value.preset)
        assertEquals(1_800L, timer.state.value.remainingSeconds)
        assertEquals(1, scheduler.activeTaskCount())

        scheduler.advanceBy(10L * 60_000L)
        assertEquals(0, expiryCalls)
        assertTrue(timer.state.value.isActive)

        scheduler.advanceBy(20L * 60_000L)
        assertEquals(1, expiryCalls)
        assertFalse(timer.state.value.isActive)
    }

    @Test
    fun expiryDelegatesExactlyOnceAtDeadlineAndNeverBefore() {
        val scheduler = FakeSleepTimerScheduler()
        var expiryCalls = 0
        val timer = SleepTimerController(scheduler) { expiryCalls += 1 }

        timer.setPreset(SleepTimerPreset.MINUTES_15)
        scheduler.advanceBy(15L * 60_000L - 1L)

        assertEquals(0, expiryCalls)
        assertTrue(timer.state.value.isActive)
        assertEquals(1L, timer.state.value.remainingSeconds)

        scheduler.advanceBy(1L)
        assertEquals(1, expiryCalls)
        assertFalse(timer.state.value.isActive)
        assertEquals(SleepTimerPreset.OFF, timer.state.value.preset)

        scheduler.advanceBy(60L * 60_000L)
        assertEquals(1, expiryCalls)
    }

    private class FakeSleepTimerScheduler : SleepTimerScheduler {
        private data class Task(
            val runAtMillis: Long,
            val order: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false,
        )

        private var nowMillis = 0L
        private var nextOrder = 0L
        private val tasks = mutableListOf<Task>()

        override fun nowMillis(): Long = nowMillis

        override fun schedule(delayMillis: Long, action: () -> Unit): SleepTimerHandle {
            val task = Task(
                runAtMillis = nowMillis + delayMillis.coerceAtLeast(0L),
                order = nextOrder++,
                action = action,
            )
            tasks += task
            return SleepTimerHandle { task.cancelled = true }
        }

        fun activeTaskCount(): Int = tasks.count { !it.cancelled }

        fun advanceBy(deltaMillis: Long) {
            require(deltaMillis >= 0L)
            val target = nowMillis + deltaMillis
            while (true) {
                val next = tasks
                    .asSequence()
                    .filter { !it.cancelled && it.runAtMillis <= target }
                    .minWithOrNull(compareBy<Task> { it.runAtMillis }.thenBy { it.order })
                    ?: break
                tasks.remove(next)
                nowMillis = next.runAtMillis
                next.action()
            }
            nowMillis = target
        }
    }
}
