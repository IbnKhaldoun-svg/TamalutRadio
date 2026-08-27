package com.tamalut.radio.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoreModelTest {
    @Test
    fun `station id rejects blank values`() {
        assertFailsWith<IllegalArgumentException> {
            StationId("   ")
        }
    }

    @Test
    fun `radio station preserves primary then fallback playback order`() {
        val primary = StreamEndpoint("https://example.test/primary.mp3")
        val fallback1 = StreamEndpoint("https://example.test/fallback-1.mp3")
        val fallback2 = StreamEndpoint("https://example.test/fallback-2.mp3")

        val station = RadioStation(
            id = StationId("radio-test"),
            name = "Radio Test",
            primaryStream = primary,
            fallbackStreams = listOf(fallback1, fallback2),
        )

        assertEquals(listOf(primary, fallback1, fallback2), station.playbackStreams)
    }

    @Test
    fun `stream endpoint rejects blank url`() {
        assertFailsWith<IllegalArgumentException> {
            StreamEndpoint("")
        }
    }

    @Test
    fun `recently played rejects negative timestamps`() {
        val media = MediaItemSummary(
            id = MediaId("station:radio-test"),
            title = "Radio Test",
            sourceType = MediaSourceType.RADIO,
        )

        assertFailsWith<IllegalArgumentException> {
            RecentlyPlayedEntry(media = media, playedAtEpochMillis = -1L)
        }
    }
}
