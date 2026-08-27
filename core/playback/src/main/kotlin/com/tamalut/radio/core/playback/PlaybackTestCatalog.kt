package com.tamalut.radio.core.playback

import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint

object PlaybackTestCatalog {
    const val RADIO_AZAWAN_MEDIA_ID = "radio:radio-azawan"

    val stations: List<RadioStation> = listOf(
        station(
            id = "radio-azawan",
            name = "Radio Azawan",
            streamUrl = "https://az-maroc.ice.infomaniak.ch/az-maroc-128.mp3",
        ),
        station(
            id = "hit-radio-maroc",
            name = "HIT RADIO Maroc",
            streamUrl = "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3",
        ),
        station(
            id = "radio-mars",
            name = "Radio Mars",
            streamUrl = "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3",
        ),
    )

    fun mediaIdFor(station: RadioStation): String = "radio:${station.id.value}"

    internal fun resolve(mediaId: String): ResolvedRadioPlaylist? {
        val startIndex = stations.indexOfFirst { mediaIdFor(it) == mediaId }
        return if (startIndex >= 0) {
            ResolvedRadioPlaylist(stations = stations, startIndex = startIndex)
        } else {
            null
        }
    }

    private fun station(id: String, name: String, streamUrl: String): RadioStation = RadioStation(
        id = StationId(id),
        name = name,
        primaryStream = StreamEndpoint(streamUrl),
        fallbackStreams = emptyList(),
    )
}

internal data class ResolvedRadioPlaylist(
    val stations: List<RadioStation>,
    val startIndex: Int,
)
