package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId

enum class RadioStationFilter(val label: String) {
    ALL("Tutte"),
    MOROCCO("Marocco"),
    ITALY("Italia"),
    SPORT("Sport"),
    UK("UK"),
    PERSONAL("Personali"),
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
        customStationIds: Set<StationId> = emptySet(),
    ): RadioStationFilter? = when {
        station.id in customStationIds -> RadioStationFilter.PERSONAL
        station.id.value in moroccoStationIds -> RadioStationFilter.MOROCCO
        station.id.value in italyStationIds -> RadioStationFilter.ITALY
        station.id.value in sportStationIds -> RadioStationFilter.SPORT
        station.id.value in ukStationIds -> RadioStationFilter.UK
        else -> null
    }

    fun apply(
        stations: List<RadioStation>,
        filter: RadioStationFilter,
        customStationIds: Set<StationId> = emptySet(),
    ): List<RadioStation> = when (filter) {
        RadioStationFilter.ALL -> stations
        else -> stations.filter { station ->
            filterFor(station, customStationIds) == filter
        }
    }
}
