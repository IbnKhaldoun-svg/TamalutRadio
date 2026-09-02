package com.tamalut.radio.core.data

import com.tamalut.radio.core.database.FavoriteStationDao
import com.tamalut.radio.core.database.RadioStationDao
import com.tamalut.radio.core.database.RadioStationPersistenceRecord
import com.tamalut.radio.core.database.RadioStationWithFallbacks
import com.tamalut.radio.core.database.toDomain
import com.tamalut.radio.core.database.toPersistenceRecord
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId

class RadioStationRepository(
    private val stationDao: RadioStationDao,
    private val favoriteStationDao: FavoriteStationDao,
) {
    suspend fun seedInitialCatalog() {
        repairLegacyAtbirLabel()
        repairLegacyAswatStream()
        retireLegacyRadioMaria()
        repairLegacyBbcStreams()
        InitialRadioCatalog.stations.forEach { station ->
            if (stationDao.getStation(station.id.value) == null) {
                persist(station.toPersistenceRecord(isCustom = false))
            }
        }
    }

    suspend fun getStations(favoritesFirst: Boolean = true): List<RadioStation> {
        val stations = orderedRecords(stationDao.getAllStations()).map { it.toDomain() }
        if (!favoritesFirst) return stations

        val favoriteOrder = favoriteStationDao.getFavoriteStationIds()
            .withIndex()
            .associate { (index, id) -> id to index }
        val baseOrder = stations.withIndex().associate { (index, station) -> station.id.value to index }

        return stations.sortedWith(
            compareBy<RadioStation> { favoriteOrder[it.id.value] ?: Int.MAX_VALUE }
                .thenBy { baseOrder[it.id.value] ?: Int.MAX_VALUE },
        )
    }

    suspend fun getStation(stationId: StationId): RadioStation? =
        stationDao.getStation(stationId.value)?.toDomain()

    suspend fun saveCustomStation(station: RadioStation) {
        persist(station.toPersistenceRecord(isCustom = true))
    }

    suspend fun removeCustomStation(stationId: StationId): Boolean {
        val stored = stationDao.getStation(stationId.value) ?: return false
        if (!stored.station.isCustom) return false
        stationDao.deleteStation(stationId.value)
        return true
    }

    private suspend fun repairLegacyAtbirLabel() {
        val stored = stationDao.getStation(LEGACY_ATBIR_ID) ?: return
        val entity = stored.station
        if (
            !entity.isCustom &&
            entity.name == LEGACY_ATBIR_NAME &&
            entity.primaryStreamUrl == LEGACY_ATBIR_STREAM
        ) {
            stationDao.upsertStation(entity.copy(name = CORRECTED_ATBIR_NAME))
        }
    }

    private suspend fun repairLegacyAswatStream() {
        val stored = stationDao.getStation(LEGACY_ASWAT_ID) ?: return
        val entity = stored.station
        if (
            !entity.isCustom &&
            entity.primaryStreamUrl == LEGACY_ASWAT_STREAM
        ) {
            stationDao.upsertStation(entity.copy(primaryStreamUrl = CORRECTED_ASWAT_STREAM))
        }
    }

    private suspend fun retireLegacyRadioMaria() {
        val stored = stationDao.getStation(LEGACY_RADIO_MARIA_ID) ?: return
        val entity = stored.station
        if (
            !entity.isCustom &&
            entity.name == LEGACY_RADIO_MARIA_NAME &&
            entity.primaryStreamUrl == LEGACY_RADIO_MARIA_STREAM
        ) {
            stationDao.deleteStation(LEGACY_RADIO_MARIA_ID)
        }
    }

    private suspend fun repairLegacyBbcStreams() {
        repairLegacyBbcStream(BBC_RADIO_1_ID, LEGACY_BBC_RADIO_1_STREAM, CORRECTED_BBC_RADIO_1_STREAM)
        repairLegacyBbcStream(BBC_RADIO_2_ID, LEGACY_BBC_RADIO_2_STREAM, CORRECTED_BBC_RADIO_2_STREAM)
    }

    private suspend fun repairLegacyBbcStream(
        stationId: String,
        legacyStream: String,
        correctedStream: String,
    ) {
        val stored = stationDao.getStation(stationId) ?: return
        val entity = stored.station
        if (!entity.isCustom && entity.primaryStreamUrl == legacyStream) {
            stationDao.upsertStation(entity.copy(primaryStreamUrl = correctedStream))
        }
    }

    private fun orderedRecords(records: List<RadioStationWithFallbacks>): List<RadioStationWithFallbacks> {
        val catalogOrder = InitialRadioCatalog.stations
            .withIndex()
            .associate { (index, station) -> station.id.value to index }

        return records.sortedWith(
            compareBy<RadioStationWithFallbacks> { record ->
                when {
                    record.station.stationId in catalogOrder -> 0
                    !record.station.isCustom -> 1
                    else -> 2
                }
            }.thenBy { record -> catalogOrder[record.station.stationId] ?: Int.MAX_VALUE }
                .thenBy { record ->
                    if (record.station.stationId in catalogOrder) "" else record.station.name.lowercase()
                }
                .thenBy { record -> record.station.stationId },
        )
    }

    private suspend fun persist(record: RadioStationPersistenceRecord) {
        stationDao.deleteFallbackStreams(record.station.stationId)
        stationDao.upsertStation(record.station)
        if (record.fallbackStreams.isNotEmpty()) {
            stationDao.upsertFallbackStreams(record.fallbackStreams)
        }
    }

    private companion object {
        const val LEGACY_ATBIR_ID = "radio-plus-agadir"
        const val LEGACY_ATBIR_NAME = "Radio Plus Agadir 92.4"
        const val LEGACY_ATBIR_STREAM = "https://stream-158.zeno.fm/bqdbb6hd0neuv"
        const val CORRECTED_ATBIR_NAME = "Radio Atbir"
        const val LEGACY_ASWAT_ID = "aswat-fm"
        const val LEGACY_ASWAT_STREAM = "https://broadcast.ice.infomaniak.ch/aswat-high.mp3"
        const val CORRECTED_ASWAT_STREAM = "https://aswat.ice.infomaniak.ch/aswat-high.mp3"
        const val LEGACY_RADIO_MARIA_ID = "radio-maria"
        const val LEGACY_RADIO_MARIA_NAME = "Radio Maria"
        const val LEGACY_RADIO_MARIA_STREAM = "https://dreamsiteradiocp4.com/proxy/rmitaliamontecarlo?mp=/stream"
        const val BBC_RADIO_1_ID = "bbc-radio-1"
        const val LEGACY_BBC_RADIO_1_STREAM = "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/ak/bbc_radio_one.m3u8"
        const val CORRECTED_BBC_RADIO_1_STREAM = "https://as-hls-ww-live.akamaized.net/pool_01505109/live/ww/bbc_radio_one/bbc_radio_one.isml/bbc_radio_one-audio%3d96000.norewind.m3u8"
        const val BBC_RADIO_2_ID = "bbc-radio-2"
        const val LEGACY_BBC_RADIO_2_STREAM = "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/cf/bbc_radio_two.m3u8"
        const val CORRECTED_BBC_RADIO_2_STREAM = "https://as-hls-ww-live.akamaized.net/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d96000.norewind.m3u8"
    }
}
