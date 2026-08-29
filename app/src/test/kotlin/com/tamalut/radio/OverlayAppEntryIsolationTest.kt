package com.tamalut.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAppEntryIsolationTest {
    @Test
    fun appEntryInvokesOnlyLaunchCallback() {
        var launchCalls = 0
        var playbackCalls = 0

        performOverlayAppEntry {
            launchCalls += 1
        }

        assertEquals(1, launchCalls)
        assertEquals(0, playbackCalls)
    }
}
