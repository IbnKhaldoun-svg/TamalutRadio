package com.tamalut.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayAutoCollapseTimerTest {
    @Test
    fun armSchedulesFourSecondTimeout() {
        val scheduler = FakeScheduler()
        var timeoutCalls = 0
        val timer = OverlayAutoCollapseTimer(
            schedule = scheduler::schedule,
            onTimeout = { timeoutCalls += 1 },
        )

        timer.arm()

        assertEquals(OVERLAY_AUTO_COLLAPSE_DELAY_MILLIS, scheduler.tasks.single().delayMillis)
        scheduler.fire(0)
        assertEquals(1, timeoutCalls)
    }

    @Test
    fun rearmCancelsOldTaskAndOnlyNewestCanCollapse() {
        val scheduler = FakeScheduler()
        var timeoutCalls = 0
        val timer = OverlayAutoCollapseTimer(
            schedule = scheduler::schedule,
            onTimeout = { timeoutCalls += 1 },
        )

        timer.arm()
        timer.arm()

        assertTrue(scheduler.tasks[0].cancelled)
        scheduler.fire(0)
        assertEquals(0, timeoutCalls)
        scheduler.fire(1)
        assertEquals(1, timeoutCalls)
    }

    @Test
    fun cancelInvalidatesEvenAStaleCallbackThatStillFires() {
        val scheduler = FakeScheduler()
        var timeoutCalls = 0
        val timer = OverlayAutoCollapseTimer(
            schedule = scheduler::schedule,
            onTimeout = { timeoutCalls += 1 },
        )

        timer.arm()
        timer.cancel()

        assertTrue(scheduler.tasks.single().cancelled)
        scheduler.fire(0)
        assertEquals(0, timeoutCalls)
    }

    private class FakeScheduler {
        data class Task(
            val delayMillis: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false,
        )

        val tasks = mutableListOf<Task>()

        fun schedule(delayMillis: Long, action: () -> Unit): OverlayScheduledTask {
            val task = Task(delayMillis, action)
            tasks += task
            return OverlayScheduledTask { task.cancelled = true }
        }

        fun fire(index: Int) {
            tasks[index].action()
        }
    }
}
