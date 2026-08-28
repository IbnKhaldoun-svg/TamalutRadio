package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.data.InitialRadioCatalog
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationGroupingTest {
    @Test
    fun currentCatalogUsesUserFiltersAndKeepsRadioMarsInMorocco() {
        val stations = InitialRadioCatalog.stations
        val morocco = RadioStationFiltering.apply(stations, RadioStationFilter.MOROCCO)
        val italy = RadioStationFiltering.apply(stations, RadioStationFilter.ITALY)
        val sport = RadioStationFiltering.apply(stations, RadioStationFilter.SPORT)

        assertEquals(7, morocco.size)
        assertEquals(1, italy.size)
        assertEquals(1, sport.size)
        assertTrue(morocco.any { it.id.value == "radio-mars" })
        assertFalse(sport.any { it.id.value == "radio-mars" })
        assertEquals(listOf("radio-sportiva"), sport.map { it.id.value })
        assertEquals(
            RadioStationFilter.MOROCCO,
            RadioStationFiltering.filterFor(stations.first { it.id.value == "radio-mars" }),
        )
    }

    @Test
    fun allFilterPreservesFlatOrderAndUnknownStationsRemainVisible() {
        val unknown = RadioStation(
            id = StationId("future-station"),
            name = "Future Station",
            primaryStream = StreamEndpoint("https://example.com/live.mp3"),
        )
        val stations = InitialRadioCatalog.stations + unknown

        assertEquals(stations, RadioStationFiltering.apply(stations, RadioStationFilter.ALL))
        assertNull(RadioStationFiltering.filterFor(unknown))
        assertFalse(RadioStationFiltering.apply(stations, RadioStationFilter.MOROCCO).contains(unknown))
        assertFalse(RadioStationFiltering.apply(stations, RadioStationFilter.ITALY).contains(unknown))
        assertFalse(RadioStationFiltering.apply(stations, RadioStationFilter.SPORT).contains(unknown))
    }

    @Test
    fun filterOptionsAreStableAndUserFacing() {
        assertEquals(
            listOf("Tutte", "Marocco", "Italia", "Sport"),
            RadioStationFilter.entries.map { it.label },
        )
    }
}
