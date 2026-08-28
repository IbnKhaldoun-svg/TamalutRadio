package com.tamalut.radio

import com.tamalut.radio.core.playback.PlaybackLaunchContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationLaunchRoutingTest {
    @Test
    fun mediaSessionLaunchActionRoutesToNowPlaying() {
        assertEquals(
            MainDestination.NOW_PLAYING,
            destinationForLaunchAction(PlaybackLaunchContract.ACTION_OPEN_NOW_PLAYING),
        )
    }

    @Test
    fun ordinaryLaunchDoesNotForceNowPlaying() {
        assertNull(destinationForLaunchAction(null))
        assertNull(destinationForLaunchAction("android.intent.action.MAIN"))
    }
}
