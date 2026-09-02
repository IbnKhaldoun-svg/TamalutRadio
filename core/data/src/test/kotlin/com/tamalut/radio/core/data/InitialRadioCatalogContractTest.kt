package com.tamalut.radio.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialRadioCatalogContractTest {
    @Test
    fun catalogHasExactlyFiftyTwoUniqueStationsInApprovedOrder() {
        val stations = InitialRadioCatalog.stations
        val ids = stations.map { it.id.value }
        val primaryUrls = stations.map { it.primaryStream.url }

        assertEquals(EXPECTED_IDS, ids)
        assertEquals(52, stations.size)
        assertEquals(52, ids.distinct().size)
        assertEquals(52, primaryUrls.distinct().size)
        assertTrue(stations.none { it.name.contains("Radio Plus Agadir", ignoreCase = true) })
        assertEquals("Radio Atbir", stations.first { it.id.value == "radio-plus-agadir" }.name)
        assertEquals(1, stations.count { it.id.value == "radio-italia-smi" })
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
            "adwaa-fm-one",
            "radio-monte-carlo-doualiya",
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
            "rds-relax",
            "radio-subasio",
            "radio-zeta",
            "radio-bruno",
            "radiofreccia",
            "rai-isoradio",
            "rai-radio-3-classica",
            "radio-maria",
            "radio-radicale",
            "radio-cuore",
            "bbc-radio-1",
            "bbc-radio-2",
            "bbc-radio-4",
            "capital-fm-london",
            "heart-uk",
            "classic-fm",
            "radio-sportiva",
            "rete-sport",
        )
    }
}
