package com.tamalut.radio.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialRadioCatalogContractTest {
    @Test
    fun catalogHasExactlyThirtyNineUniqueStationsInApprovedOrder() {
        val stations = InitialRadioCatalog.stations
        val ids = stations.map { it.id.value }
        val primaryUrls = stations.map { it.primaryStream.url }

        assertEquals(EXPECTED_IDS, ids)
        assertEquals(39, stations.size)
        assertEquals(39, ids.distinct().size)
        assertEquals(39, primaryUrls.distinct().size)
        assertTrue(stations.none { it.name.contains("Radio Plus Agadir", ignoreCase = true) })
        assertEquals("Radio Atbir", stations.first { it.id.value == "radio-plus-agadir" }.name)
    }

    private companion object {
        val EXPECTED_IDS = listOf(
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
            "bbc-radio-1",
            "bbc-radio-2",
            "bbc-radio-4",
            "capital-fm-london",
            "heart-uk",
            "classic-fm",
            "radio-sportiva",
        )
    }
}
