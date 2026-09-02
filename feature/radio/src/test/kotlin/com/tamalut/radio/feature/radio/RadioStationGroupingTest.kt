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
    fun expandedCatalogUsesFourBuiltInCategoriesInAuthoritativeRelativeOrder() {
        val stations = InitialRadioCatalog.stations
        val morocco = RadioStationFiltering.apply(stations, RadioStationFilter.MOROCCO)
        val italy = RadioStationFiltering.apply(stations, RadioStationFilter.ITALY)
        val sport = RadioStationFiltering.apply(stations, RadioStationFilter.SPORT)
        val uk = RadioStationFiltering.apply(stations, RadioStationFilter.UK)

        assertEquals(54, stations.size)
        assertEquals(MOROCCO_IDS, morocco.map { it.id.value })
        assertEquals(ITALY_IDS, italy.map { it.id.value })
        assertEquals(SPORT_IDS, sport.map { it.id.value })
        assertEquals(UK_IDS, uk.map { it.id.value })
        assertEquals(21, morocco.size)
        assertEquals(22, italy.size)
        assertEquals(5, sport.size)
        assertEquals(6, uk.size)
        assertTrue(morocco.any { it.id.value == "radio-mars" })
        assertFalse(sport.any { it.id.value == "radio-mars" })
    }

    @Test
    fun customStationsJoinAssignedStandardCategoryAfterBuiltInsAndDynamicCategoryIsDiscovered() {
        val customSport = station("custom-sport", "Zulu Sport")
        val customJazzB = station("custom-jazz-b", "Beta Jazz")
        val customJazzA = station("custom-jazz-a", "Alpha Jazz")
        val stations = InitialRadioCatalog.stations + listOf(customJazzA, customJazzB, customSport)
        val categories = mapOf(
            customSport.id to "Sport",
            customJazzA.id to "Jazz",
            customJazzB.id to "jazz",
        )

        val sport = RadioStationFiltering.apply(stations, RadioStationFilter.SPORT, categories)
        assertEquals(SPORT_IDS + "custom-sport", sport.map { it.id.value })
        val filters = RadioStationFilter.available(categories.values)
        assertEquals(listOf("Tutte", "Marocco", "Italia", "Sport", "UK", "Jazz"), filters.map { it.label })
        val jazz = RadioStationFiltering.apply(stations, filters.last(), categories)
        assertEquals(listOf(customJazzA, customJazzB), jazz)
    }

    @Test
    fun dynamicCategoriesSortCaseInsensitivelyAndDisappearWhenNoStationReferencesThem() {
        val one = station("custom-one", "One")
        val two = station("custom-two", "Two")
        val categories = mapOf(one.id to "News", two.id to "amazigh")
        assertEquals(
            listOf("Tutte", "Marocco", "Italia", "Sport", "UK", "amazigh", "News"),
            RadioStationFilter.available(categories.values).map { it.label },
        )
        assertEquals(
            listOf("Tutte", "Marocco", "Italia", "Sport", "UK", "amazigh"),
            RadioStationFilter.available(mapOf(one.id to "amazigh").values).map { it.label },
        )
    }

    @Test
    fun allFilterPreservesFlatOrderAndUnknownStationsRemainVisibleOnlyInAllWithoutCategoryMetadata() {
        val unknown = station("future-station", "Future Station")
        val stations = InitialRadioCatalog.stations + unknown
        assertEquals(stations, RadioStationFiltering.apply(stations, RadioStationFilter.ALL))
        assertNull(RadioStationFiltering.filterFor(unknown))
        listOf(
            RadioStationFilter.MOROCCO,
            RadioStationFilter.ITALY,
            RadioStationFilter.SPORT,
            RadioStationFilter.UK,
        ).forEach {
            assertFalse(RadioStationFiltering.apply(stations, it).contains(unknown))
        }
    }

    @Test
    fun categoryRulesCanonicalizeKnownNamesReuseDynamicSpellingAndRejectNavigationNames() {
        assertEquals("Italia", RadioCategoryRules.normalize(" italia ", listOf("Jazz")))
        assertEquals("Jazz", RadioCategoryRules.normalize("jAzZ", listOf("Jazz")))
        assertTrue(RadioCategoryRules.isReservedNewCategoryName("sport"))
        assertTrue(RadioCategoryRules.isReservedNewCategoryName(" Preferiti "))
        val error = runCatching { RadioCategoryRules.normalize("Tutte", emptyList()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    private fun station(id: String, name: String) = RadioStation(
        id = StationId(id),
        name = name,
        primaryStream = StreamEndpoint("https://example.com/$id"),
    )

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
            "radiofreccia", "rai-isoradio", "rai-radio-3-classica", "radio-radicale", "radio-cuore",
        )
        val UK_IDS = listOf("bbc-radio-1", "bbc-radio-2", "bbc-radio-4", "capital-fm-london", "heart-uk", "classic-fm")
        val SPORT_IDS = listOf("radio-sportiva", "rete-sport", "on-sport-fm", "talksport", "radio-mana-mana-sport-roma")
    }
}
