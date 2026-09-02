package com.tamalut.radio.core.data

import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint

object InitialRadioCatalog {
    val stations: List<RadioStation> = listOf(
        station("medi1-radio", "Medi1 Radio", "https://cdn.live.easybroadcast.io/live/83_medi1radio-maghreb_8s9i4bn/playlist.m3u8"),
        station("hit-radio-maroc", "HIT RADIO Maroc", "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3"),
        station("chada-fm", "Chada FM", "https://stream.bodkas.com/playlist?id=chadafmradio"),
        station("atlantic-radio", "Atlantic Radio", "https://atlantic-sonic.nindohost.net:9300/stream"),
        station("cap-radio", "Cap Radio", "https://listen.radioking.com/radio/710810/stream/776366"),
        station("med-radio", "Med Radio", "https://medradio.ice.infomaniak.ch/medradio-128.mp3"),
        station("radio-mars", "Radio Mars", "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3"),
        station("radio-plus-agadir", "Radio Atbir", "https://stream-158.zeno.fm/bqdbb6hd0neuv"),
        station("radio-azawan", "Radio Azawan", "https://az-maroc.ice.infomaniak.ch/az-maroc-128.mp3"),
        station("aswat-fm", "Aswat FM", "https://aswat.ice.infomaniak.ch/aswat-high.mp3"),
        station("mfm-radio", "MFM Radio", "https://a5.asurahosting.com:7980/radio.mp3"),
        station("radio-medina-fm", "Radio Medina FM", "https://medinafm.ice.infomaniak.ch/medinafm-128.mp3"),
        station("medina-fm-amazigh", "Medina FM Amazigh", "https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3"),
        station("ness-radio", "Ness Radio", "https://radio.nessradio.net:8212/nessradio-hd"),
        station("radio-manarat", "Radio Manarat", "https://listen.radioking.com/radio/252934/stream/297385"),
        station("radio-tanger-med", "Radio Tanger Med", "https://radiotangermed-22.ice.infomaniak.ch/radiotangermed-22-128.mp3"),
        station("radio-yabiladi", "Radio Yabiladi", "https://radio.yabiladi.com:8002/;stream.mp3"),
        station("radio-achkid-fm", "Radio Achkid FM", "https://stream.zeno.fm/7nqu31p6xg0uv"),
        station("radio-star-maroc-fm", "Radio Star Maroc FM", "https://a2.asurahosting.com:6100/radio.mp3"),
        station("adwaa-fm-one", "Adwaa FM One", "https://stream.zeno.fm/5bxh2nh0x1zuv"),
        station("radio-monte-carlo-doualiya", "Radio Monte Carlo Doualiya", "https://montecarlodoualiya128k.ice.infomaniak.ch/mc-doualiya.mp3"),
        station("rtl-102-5", "RTL 102.5", "https://dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/live/S97044836/tbbP8T1ZRPBL/playlist_audio.m3u8"),
        station("radio-deejay", "Radio Deejay", "https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiodeejay/radiodeejay/master_ma.m3u8"),
        station("radio-105", "Radio 105", "https://icecast.unitedradio.it/Radio105.mp3"),
        station("rds-100-grandi-successi", "RDS 100% Grandi Successi", "https://stream.rds.radio/audio/rds.stream_aac64/chunklist.m3u8"),
        station("radio-italia-smi", "Radio Italia Solo Musica Italiana", "https://radioitaliasmi.akamaized.net/hls/live/2093120/RISMI/stream01/streamPlaylist.m3u8"),
        station("virgin-radio-italia", "Virgin Radio Italia", "https://icecast.unitedradio.it/Virgin.mp3"),
        station("radio-capital", "Radio Capital", "https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiocapital/radiocapital/master_ma.m3u8"),
        station("m2o", "m2o", "https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiom2o/radiom2o/master_ma.m3u8"),
        station("radio-monte-carlo", "Radio Monte Carlo (RMC)", "https://icy.unitedradio.it/RMC.aac"),
        station("r101", "R101", "https://icecast.unitedradio.it/r101_mp3"),
        station("rai-radio-1", "Rai Radio 1", "https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S16355530/Q4zh3NTu28Rx/icecast"),
        station("rai-radio-2", "Rai Radio 2", "https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S35942484/yp5F67151K92/icecast"),
        station("rai-radio-3", "Rai Radio 3", "https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S56630579/yEbkcBtIoSwd/icecast"),
        station("rds-relax", "RDS Relax", "https://stream.rds.radio/audio/rdsrelax.stream_aac/playlist.m3u8"),
        station("radio-subasio", "Radio Subasio", "https://icy.unitedradio.it/Subasio.mp3"),
        station("radio-zeta", "Radio Zeta", "https://streamingv2.shoutcast.com/radio-zeta_48.aac"),
        station("radio-bruno", "Radio Bruno", "https://router.xdevel.com/audio4s975355-254/stream/icecast.audio"),
        station("radiofreccia", "Radiofreccia", "https://dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/live/S3160845/0tuSetc8UFkF/playlist_audio.m3u8"),
        station("rai-isoradio", "Rai Isoradio", "https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S3822289/9T4F68Q3TT4m/icecast"),
        station("rai-radio-3-classica", "Rai Radio 3 Classica", "https://radiotreclassica-live.akamaized.net/hls/live/2032595/radiotreclassica/radiotreclassica/playlist.m3u8"),
        station("radio-radicale", "Radio Radicale", "https://live.radioradicale.it/live.mp3"),
        station("radio-cuore", "Radio Cuore", "https://stream10.xdevel.com/audio32s975552-1839/stream/icecast.audio"),
        station("bbc-radio-1", "BBC Radio 1", "https://as-hls-ww-live.akamaized.net/pool_01505109/live/ww/bbc_radio_one/bbc_radio_one.isml/bbc_radio_one-audio%3d96000.norewind.m3u8"),
        station("bbc-radio-2", "BBC Radio 2", "https://as-hls-ww-live.akamaized.net/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d96000.norewind.m3u8"),
        station("bbc-radio-4", "BBC Radio 4", "https://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d128000.norewind.m3u8"),
        station("capital-fm-london", "Capital FM London", "https://media-ssl.musicradio.com/CapitalMP3"),
        station("heart-uk", "Heart UK", "https://media-ssl.musicradio.com/HeartUK"),
        station("classic-fm", "Classic FM", "https://media-ssl.musicradio.com/ClassicFMMP3"),
        station("radio-sportiva", "Radio Sportiva", "https://sportiva.inmystream.it/stream/sportiva"),
        station("rete-sport", "Rete Sport", "https://icecast.ithost.it/retesport.ogg"),
    )

    private fun station(id: String, name: String, streamUrl: String): RadioStation = RadioStation(
        id = StationId(id),
        name = name,
        primaryStream = StreamEndpoint(streamUrl),
        fallbackStreams = emptyList(),
    )
}
