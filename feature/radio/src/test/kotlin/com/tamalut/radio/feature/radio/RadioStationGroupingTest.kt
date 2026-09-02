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

        assertEquals(52, stations.size)
        assertEquals(MOROCCO_IDS, morocco.map { it.id.value })
        assertEquals(ITALY_IDS, italy.map { it.id.value })
        assertEquals(SPORT_IDS, sport.map { it.id.value })
        assertEquals(UK_IDS, uk.map { it.id.value })
        assertEquals(21, morocco.size)
        assertEquals(23, italy.size)
        assertEquals(2, sport.size)
        assertEquals(6, uk.size)
        assertTrue(morocco.any { it.id.value == "radio-mars" })
        assertTrue(morocco.any { it.id.value == "radio-monte-carlo-doualiya" })
        assertFalse(sport.any { it.id.value == "radio-mars" })
        assertEquals(RadioStationFilter.SPORT, RadioStationFiltering.filterFor(stations.first { it.id.value == "rete-sport" }))
        assertEquals(RadioStationFilter.MOROCCO, RadioStationFiltering.filterFor(stations.first { it.id.value == "radio-monte-carlo-doualiya" }))
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
        listOf(RadioStationFilter.MOROCCO, RadioStationFilter.ITALY, RadioStationFilter.SPORT, RadioStationFilter.UK).forEach {
            assertFalse(RadioStationFiltering.apply(stations, it).contains(unknown))
        }
    }

    @Test
    fun filterOptionsAreStableAndUserFacing() {
        assertEquals(listOf("Tutte", "Marocco", "Italia", "Sport", "UK"), RadioStationFilter.entries.map { it.label })
    }

    private companion object {
        val MOROCCO_IDS = listOf(
            "medi1-radio", "hit-radio-maroc", "chada-fm", "atlantic-radio", "cap-radio", "med-radio",
            "radio-mars", "radio-plus-agadir", "radio-azawan", "aswat-fm", "mfm-radio", "radio-medina-fm",
            "medina-fm-amazigh", "ness-radio", "radio-manarat", "radio-tanger-med", "radio-yabiladi",
            "radio-achkid-fm", "radio-star-maroc-fm", "adwaa-fm-one", "radio-monte-carlo-doualiya",
        )
        val ITALY_IDS = listOf(
            "rtl-102-5", "radio-deejay", "radio-105", "rds-100-grandi-successi", "radio-italia-smi",
            "virgin-radio-italia", "radio-capital", "m2o", "radio-monte-carlo", "r101", "rai-radio-1",
            "rai-radio-2", "rai-radio-3", "rds-relax", "radio-subasio", "radio-zeta", "radio-bruno",
            "radiofreccia", "rai-isoradio", "rai-radio-3-classica", "radio-maria", "radio-radicale", "radio-cuore",
        )
        val UK_IDS = listOf("bbc-radio-1", "bbc-radio-2", "bbc-radio-4", "capital-fm-london", "heart-uk", "classic-fm")
        val SPORT_IDS = listOf("radio-sportiva", "rete-sport")
    }
}
