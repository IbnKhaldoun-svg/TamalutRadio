package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.data.InitialRadioCatalog
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioStationGroupingTest {
    @Test
    fun currentCatalogIsGroupedInRequestedOrderWithoutDuplicates() {
        val groups = RadioStationGrouping.group(InitialRadioCatalog.stations)

        assertEquals(
            listOf(RadioListSection.MOROCCO, RadioListSection.ITALY, RadioListSection.SPORT),
            groups.map { it.section },
        )
        assertEquals(listOf(6, 1, 2), groups.map { it.stations.size })

        val groupedIds = groups.flatMap { it.stations }.map { it.id.value }
        assertEquals(InitialRadioCatalog.stations.size, groupedIds.size)
        assertEquals(groupedIds.size, groupedIds.toSet().size)
        assertEquals(
            InitialRadioCatalog.stations.map { it.id.value }.toSet(),
            groupedIds.toSet(),
        )
    }

    @Test
    fun unknownFutureStationFallsBackToOther() {
        val station = RadioStation(
            id = StationId("future-station"),
            name = "Future Station",
            primaryStream = StreamEndpoint("https://example.com/live.mp3"),
        )

        assertEquals(RadioListSection.OTHER, RadioStationGrouping.sectionFor(station))
        assertEquals(RadioListSection.OTHER, RadioStationGrouping.group(listOf(station)).single().section)
    }
}
