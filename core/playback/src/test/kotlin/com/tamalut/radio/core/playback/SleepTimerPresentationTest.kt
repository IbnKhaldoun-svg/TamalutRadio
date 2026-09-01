package com.tamalut.radio.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerPresentationTest {
    @Test
    fun notificationContentTextUsesSharedCountdownFormatting() {
        assertEquals("14:59", formatSleepTimerRemaining(899L))
        assertEquals("1:00:00", formatSleepTimerRemaining(3_600L))
        assertEquals("Spegnimento tra 12:34", sleepTimerNotificationContentText(754L))
        assertNull(sleepTimerNotificationContentText(null))
        assertNull(sleepTimerNotificationContentText(0L))
    }

    @Test
    fun bridgeProjectsOnlyActivePositiveRemainingTime() {
        SleepTimerNotificationBridge.publish(
            SleepTimerState(
                preset = SleepTimerPreset.MINUTES_15,
                remainingSeconds = 899L,
            ),
        )
        assertEquals(899L, SleepTimerNotificationBridge.remainingSeconds.value)

        SleepTimerNotificationBridge.publish(SleepTimerState())
        assertNull(SleepTimerNotificationBridge.remainingSeconds.value)
    }
}
