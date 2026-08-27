package com.tamalut.radio.core.database

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaItemSummary
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.RecentlyPlayedEntry
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMappingsTest {
    @Test
    fun radioStationRoundTripPreservesFallbackPriority() {
        val station = RadioStation(
            id = StationId("radio-azawan"),
            name = "Radio Azawan",
            primaryStream = StreamEndpoint("https://example.test/primary.mp3"),
            fallbackStreams = listOf(
                StreamEndpoint("https://example.test/fallback-1.mp3"),
                StreamEndpoint("https://example.test/fallback-2.mp3"),
            ),
        )

        val record = station.toPersistenceRecord(isCustom = false)
        val relationReturnedOutOfOrder = RadioStationWithFallbacks(
            station = record.station,
            fallbackStreams = record.fallbackStreams.reversed(),
        )

        assertEquals(station, relationReturnedOutOfOrder.toDomain())
        assertEquals(listOf(0, 1), record.fallbackStreams.map { it.position })
    }

    @Test
    fun customStationFlagIsPersistenceOnlyMetadata() {
        val station = RadioStation(
            id = StationId("custom-1"),
            name = "Custom",
            primaryStream = StreamEndpoint("https://example.test/custom.mp3"),
        )

        val record = station.toPersistenceRecord(isCustom = true)

        assertTrue(record.station.isCustom)
        assertEquals(station, RadioStationWithFallbacks(record.station, emptyList()).toDomain())
    }

    @Test
    fun recentlyPlayedRoundTripPreservesApprovedMetadata() {
        val entry = RecentlyPlayedEntry(
            media = MediaItemSummary(
                id = MediaId("track-1"),
                title = "Track One",
                subtitle = "Artist",
                sourceType = MediaSourceType.LOCAL,
            ),
            playedAtEpochMillis = 123456L,
        )

        val entity = entry.toEntity()

        assertEquals(entry, entity.toDomainOrNull())
        assertNull(entity.stationId)
    }

    @Test
    fun unknownSourceTypeIsRejectedDefensively() {
        val entity = RecentlyPlayedEntity(
            mediaId = "broken",
            sourceType = "UNKNOWN",
            title = "Broken",
            subtitle = null,
            stationId = null,
            playedAtEpochMillis = 0L,
        )

        assertNull(entity.toDomainOrNull())
    }
}
