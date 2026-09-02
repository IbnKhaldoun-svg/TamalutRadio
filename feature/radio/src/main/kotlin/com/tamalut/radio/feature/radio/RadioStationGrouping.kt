package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import java.util.Locale

data class RadioStationFilter private constructor(
    val categoryName: String?,
) {
    val label: String
        get() = categoryName ?: "Tutte"

    companion object {
        val ALL = RadioStationFilter(categoryName = null)
        val MOROCCO = RadioStationFilter(categoryName = "Marocco")
        val ITALY = RadioStationFilter(categoryName = "Italia")
        val SPORT = RadioStationFilter(categoryName = "Sport")
        val UK = RadioStationFilter(categoryName = "UK")

        val standard: List<RadioStationFilter> = listOf(ALL, MOROCCO, ITALY, SPORT, UK)
        val standardCategories: List<String> = standard.mapNotNull { it.categoryName }

        fun category(name: String): RadioStationFilter =
            standard.firstOrNull { it.categoryName?.equals(name, ignoreCase = true) == true }
                ?: RadioStationFilter(name.trim())

        fun available(customCategories: Collection<String>): List<RadioStationFilter> {
            val dynamic = customCategories
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filterNot { candidate -> standardCategories.any { it.equals(candidate, ignoreCase = true) } }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .map(::RadioStationFilter)
            return standard + dynamic
        }
    }
}

object RadioCategoryRules {
    const val LEGACY_CATEGORY = "Altro"
    val standardCategories: List<String> = RadioStationFilter.standardCategories
    private val reservedNavigationLabels = listOf("Tutte", "Preferiti")

    fun normalize(category: String, existingCategories: Collection<String>): String {
        val trimmed = category.trim()
        require(trimmed.isNotEmpty()) { "Seleziona una categoria" }

        standardCategories.firstOrNull { it.equals(trimmed, ignoreCase = true) }?.let { return it }
        require(reservedNavigationLabels.none { it.equals(trimmed, ignoreCase = true) }) {
            "Questo nome categoria è riservato"
        }
        existingCategories.firstOrNull { it.equals(trimmed, ignoreCase = true) }?.let { return it }
        return trimmed
    }

    fun isReservedNewCategoryName(category: String): Boolean {
        val trimmed = category.trim()
        return standardCategories.any { it.equals(trimmed, ignoreCase = true) } ||
            reservedNavigationLabels.any { it.equals(trimmed, ignoreCase = true) }
    }
}

object RadioStationFiltering {
    private val moroccoStationIds = setOf(
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
    )
    private val italyStationIds = setOf(
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
        "radio-radicale",
        "radio-cuore",
    )
    private val sportStationIds = setOf(
        "radio-sportiva",
        "rete-sport",
        "on-sport-fm",
        "talksport",
        "radio-mana-mana-sport-roma",
    )
    private val ukStationIds = setOf(
        "bbc-radio-1",
        "bbc-radio-2",
        "bbc-radio-4",
        "capital-fm-london",
        "heart-uk",
        "classic-fm",
    )

    fun filterFor(
        station: RadioStation,
        customStationCategories: Map<StationId, String> = emptyMap(),
    ): RadioStationFilter? {
        customStationCategories[station.id]?.let { category ->
            return RadioStationFilter.category(category)
        }
        return when (station.id.value) {
            in moroccoStationIds -> RadioStationFilter.MOROCCO
            in italyStationIds -> RadioStationFilter.ITALY
            in sportStationIds -> RadioStationFilter.SPORT
            in ukStationIds -> RadioStationFilter.UK
            else -> null
        }
    }

    fun apply(
        stations: List<RadioStation>,
        filter: RadioStationFilter,
        customStationCategories: Map<StationId, String> = emptyMap(),
    ): List<RadioStation> {
        if (filter == RadioStationFilter.ALL) return stations
        return stations.filter { station ->
            filterFor(station, customStationCategories)?.categoryName
                ?.equals(filter.categoryName, ignoreCase = true) == true
        }
    }

    fun userDefinedCategories(customStationCategories: Map<StationId, String>): List<String> =
        RadioStationFilter.available(customStationCategories.values)
            .drop(RadioStationFilter.standard.size)
            .map(RadioStationFilter::label)
}
