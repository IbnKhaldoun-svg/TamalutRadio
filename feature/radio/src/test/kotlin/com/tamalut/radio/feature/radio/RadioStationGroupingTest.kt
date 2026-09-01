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
    fun expandedCatalogUsesFourCategoriesInAuthoritativeRelativeOrder() {
        val stations = InitialRadioCatalog.stations
        val morocco = RadioStationFiltering.apply(stations, RadioStationFilter.MOROCCO)
        val italy = RadioStationFiltering.apply(stations, RadioStationFilter.ITALY)
        val sport = RadioStationFiltering.apply(stations, RadioStationFilter.SPORT)
        val uk = RadioStationFiltering.apply(stations, RadioStationFilter.UK)

        assertEquals(MOROCCO_IDS, morocco.map { it.id.value })
        assertEquals(ITALY_IDS, italy.map { it.id.value })
        assertEquals(listOf("radio-sportiva"), sport.map { it.id.value })
        assertEquals(UK_IDS, uk.map { it.id.value })
        assertEquals(19, morocco.size)
        assertEquals(13, italy.size)
        assertEquals(1, sport.size)
        assertEquals(6, uk.size)
        assertTrue(morocco.any { it.id.value == "radio-mars" })
        assertFalse(sport.any { it.id.value == "radio-mars" })
    }

    @Test
    fun allFilterPreservesFlatOrderAndUnknownStationsRemainVisibleOnlyInAll() {
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
        assertFalse(RadioStationFiltering.apply(stations, RadioStationFilter.UK).contains(unknown))
    }

    @Test
    fun filterOptionsAreStableAndUserFacing() {
        assertEquals(
            listOf("Tutte", "Marocco", "Italia", "Sport", "UK"),
            RadioStationFilter.entries.map { it.label },
        )
    }

    private companion object {
        val MOROCCO_IDS = listOf(
            "medi1-radio",
            "hit-radio-maroc",
            "chada-fm",
            "atlantic-radio",
            "cap-radio",
            "med-radio",
            "radio-mars",
            "radio-plus-agadir",
            "radio-azawan",
            "aswat-fm",
            "mfm-radio",
            "radio-medina-fm",
            "medina-fm-amazigh",
            "ness-radio",
            "radio-manarat",
            "radio-tanger-med",
            "radio-yabiladi",
            "radio-achkid-fm",
            "radio-star-maroc-fm",
        )
        val ITALY_IDS = listOf(
            "rtl-102-5",
            "radio-deejay",
            "radio-105",
            "rds-100-grandi-successi",
            "radio-italia-smi",
            "virgin-radio-italia",
            "radio-capital",
            "m2o",
            "radio-monte-carlo",
            "r101",
            "rai-radio-1",
            "rai-radio-2",
            "rai-radio-3",
        )
        val UK_IDS = listOf(
            "bbc-radio-1",
            "bbc-radio-2",
            "bbc-radio-4",
            "capital-fm-london",
            "heart-uk",
            "classic-fm",
        )
    }
}
