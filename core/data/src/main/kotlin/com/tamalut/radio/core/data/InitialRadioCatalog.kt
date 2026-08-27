package com.tamalut.radio.core.data

import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint

object InitialRadioCatalog {
    val stations: List<RadioStation> = listOf(
        station("radio-azawan", "Radio Azawan", "https://az-maroc.ice.infomaniak.ch/az-maroc-128.mp3"),
        station("radio-plus-agadir", "Radio Plus Agadir 92.4", "https://stream-158.zeno.fm/bqdbb6hd0neuv"),
        station("hit-radio-maroc", "HIT RADIO Maroc", "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3"),
        station("radio-mars", "Radio Mars", "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3"),
        station("aswat-fm", "Aswat FM", "https://broadcast.ice.infomaniak.ch/aswat-high.mp3"),
        station("mfm-radio", "MFM Radio", "https://a5.asurahosting.com:7980/radio.mp3"),
        station("medina-fm-amazigh", "Medina FM Amazigh", "https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3"),
        station("radio-italia-smi", "Radio Italia Solo Musica Italiana", "https://radioitaliasmi.akamaized.net/hls/live/2093120/RISMI/stream01/streamPlaylist.m3u8"),
        station("radio-sportiva", "Radio Sportiva", "https://sportiva.inmystream.it/stream/sportiva"),
    )

    private fun station(id: String, name: String, streamUrl: String): RadioStation = RadioStation(
        id = StationId(id),
        name = name,
        primaryStream = StreamEndpoint(streamUrl),
        fallbackStreams = emptyList(),
    )
}
