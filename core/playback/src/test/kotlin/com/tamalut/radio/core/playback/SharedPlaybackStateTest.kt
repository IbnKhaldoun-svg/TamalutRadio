package com.tamalut.radio.core.playback

import androidx.media3.common.Player
import com.tamalut.radio.core.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPlaybackStateTest {
    @Test
    fun localRepeatModesMapToMedia3AndRadioRejectsThem() {
        assertEquals(
            Player.REPEAT_MODE_OFF,
            PlaybackModePolicy.repeatModeFor(MediaSourceType.LOCAL, PlaybackRepeatMode.OFF),
        )
        assertEquals(
            Player.REPEAT_MODE_ONE,
            PlaybackModePolicy.repeatModeFor(MediaSourceType.LOCAL, PlaybackRepeatMode.ONE),
        )
        assertEquals(
            Player.REPEAT_MODE_ALL,
            PlaybackModePolicy.repeatModeFor(MediaSourceType.LOCAL, PlaybackRepeatMode.ALL),
        )
        assertNull(PlaybackModePolicy.repeatModeFor(MediaSourceType.RADIO, PlaybackRepeatMode.ALL))
    }

    @Test
    fun localQueueDefaultsToPlaylistLoopAndRepeatCycleIsShared() {
        assertEquals(Player.REPEAT_MODE_ALL, PlaybackModePolicy.defaultRepeatModeForLocalQueue())
        assertEquals(
            PlaybackRepeatMode.ALL,
            PlaybackModePolicy.nextRepeatMode(PlaybackRepeatMode.OFF),
        )
        assertEquals(
            PlaybackRepeatMode.ONE,
            PlaybackModePolicy.nextRepeatMode(PlaybackRepeatMode.ALL),
        )
        assertEquals(
            PlaybackRepeatMode.OFF,
            PlaybackModePolicy.nextRepeatMode(PlaybackRepeatMode.ONE),
        )
    }

    @Test
    fun newLocalQueueAppliesPlaylistLoopAfterReplacingItems() {
        val calls = mutableListOf<String>()

        installNewLocalQueue(
            setItems = { calls += "set-items" },
            applyRepeatDefault = { calls += "repeat-all" },
            prepare = { calls += "prepare" },
            play = { calls += "play" },
        )

        assertEquals(listOf("set-items", "repeat-all", "prepare", "play"), calls)
    }

    @Test
    fun shuffleIsAcceptedOnlyForLocalPlayback() {
        assertEquals(true, PlaybackModePolicy.shuffleFor(MediaSourceType.LOCAL, true))
        assertEquals(false, PlaybackModePolicy.shuffleFor(MediaSourceType.LOCAL, false))
        assertNull(PlaybackModePolicy.shuffleFor(MediaSourceType.RADIO, true))
        assertNull(PlaybackModePolicy.shuffleFor(null, true))
    }

    @Test
    fun playbackStateRequiresARealSourceItemToExposeMiniPlayer() {
        assertFalse(PlaybackState(isConnected = true).hasCurrentItem)
        assertTrue(
            PlaybackState(
                isConnected = true,
                sourceType = MediaSourceType.RADIO,
                stationId = com.tamalut.radio.core.model.StationId("station"),
            ).hasCurrentItem,
        )
        assertTrue(
            PlaybackState(
                isConnected = true,
                sourceType = MediaSourceType.LOCAL,
                mediaId = com.tamalut.radio.core.model.MediaId("track"),
            ).hasCurrentItem,
        )
    }
}
