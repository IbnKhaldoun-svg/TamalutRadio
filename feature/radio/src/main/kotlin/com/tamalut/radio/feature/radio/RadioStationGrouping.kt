package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.model.RadioStation

enum class RadioListSection(val label: String) {
    MOROCCO("Marocco"),
    ITALY("Italia"),
    SPORT("Sport"),
    OTHER("Altro"),
}

data class RadioStationGroup(
    val section: RadioListSection,
    val stations: List<RadioStation>,
)

object RadioStationGrouping {
    private val moroccoStationIds = setOf(
        "radio-azawan",
        "radio-plus-agadir",
        "hit-radio-maroc",
        "aswat-fm",
        "mfm-radio",
        "medina-fm-amazigh",
    )
    private val italyStationIds = setOf("radio-italia-smi")
    private val sportStationIds = setOf("radio-mars", "radio-sportiva")

    fun sectionFor(station: RadioStation): RadioListSection = when (station.id.value) {
        in moroccoStationIds -> RadioListSection.MOROCCO
        in italyStationIds -> RadioListSection.ITALY
        in sportStationIds -> RadioListSection.SPORT
        else -> RadioListSection.OTHER
    }

    fun group(stations: List<RadioStation>): List<RadioStationGroup> {
        val grouped = stations.groupBy(::sectionFor)
        return RadioListSection.entries.mapNotNull { section ->
            grouped[section]?.takeIf { it.isNotEmpty() }?.let { sectionStations ->
                RadioStationGroup(section, sectionStations)
            }
        }
    }
}
