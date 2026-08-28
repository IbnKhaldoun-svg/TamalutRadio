package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.model.RadioStation

enum class RadioStationFilter(val label: String) {
    ALL("Tutte"),
    MOROCCO("Marocco"),
    ITALY("Italia"),
    SPORT("Sport"),
}

object RadioStationFiltering {
    private val moroccoStationIds = setOf(
        "radio-azawan",
        "radio-plus-agadir",
        "hit-radio-maroc",
        "radio-mars",
        "aswat-fm",
        "mfm-radio",
        "medina-fm-amazigh",
    )
    private val italyStationIds = setOf("radio-italia-smi")
    private val sportStationIds = setOf("radio-sportiva")

    fun filterFor(station: RadioStation): RadioStationFilter? = when (station.id.value) {
        in moroccoStationIds -> RadioStationFilter.MOROCCO
        in italyStationIds -> RadioStationFilter.ITALY
        in sportStationIds -> RadioStationFilter.SPORT
        else -> null
    }

    fun apply(
        stations: List<RadioStation>,
        filter: RadioStationFilter,
    ): List<RadioStation> = when (filter) {
        RadioStationFilter.ALL -> stations
        else -> stations.filter { station -> filterFor(station) == filter }
    }
}
