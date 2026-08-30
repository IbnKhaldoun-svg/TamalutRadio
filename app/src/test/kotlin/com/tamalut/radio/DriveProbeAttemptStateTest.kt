package com.tamalut.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveProbeAttemptStateTest {
    @Test
    fun `terminal completion reenables button and a second attempt can start`() {
        var state = DriveProbeAttemptState()

        state = state.begin()
        val firstId = requireNotNull(state.activeAttemptId)
        assertFalse(state.canStart)

        state = state.markReading(firstId)
        assertFalse(state.canStart)

        state = state.finish(firstId)
        assertTrue(state.canStart)

        state = state.begin()
        val secondId = requireNotNull(state.activeAttemptId)
        assertFalse(state.canStart)
        assertTrue(secondId > firstId)
    }

    @Test
    fun `stale completion cannot cancel a newer picker attempt`() {
        var state = DriveProbeAttemptState().begin()
        val firstId = requireNotNull(state.activeAttemptId)
        state = state.finish(firstId)
        state = state.begin()
        val secondId = requireNotNull(state.activeAttemptId)

        state = state.finish(firstId)

        assertFalse(state.canStart)
        assertTrue(state.activeAttemptId == secondId)
    }
}
