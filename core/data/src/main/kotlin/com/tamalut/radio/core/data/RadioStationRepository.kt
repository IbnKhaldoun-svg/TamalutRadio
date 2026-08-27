package com.tamalut.radio.core.data

import com.tamalut.radio.core.database.FavoriteStationDao
import com.tamalut.radio.core.database.RadioStationDao
import com.tamalut.radio.core.database.RadioStationPersistenceRecord
import com.tamalut.radio.core.database.toDomain
import com.tamalut.radio.core.database.toPersistenceRecord
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId

class RadioStationRepository(
    private val stationDao: RadioStationDao,
    private val favoriteStationDao: FavoriteStationDao,
) {
    suspend fun seedInitialCatalog() {
        InitialRadioCatalog.stations.forEach { station ->
            if (stationDao.getStation(station.id.value) == null) {
                persist(station.toPersistenceRecord(isCustom = false))
            }
        }
    }

    suspend fun getStations(favoritesFirst: Boolean = true): List<RadioStation> {
        val stations = stationDao.getAllStations().map { it.toDomain() }
        if (!favoritesFirst) return stations

        val favoriteOrder = favoriteStationDao.getFavoriteStationIds()
            .withIndex()
            .associate { (index, id) -> id to index }

        return stations.sortedWith(
            compareBy<RadioStation> { favoriteOrder[it.id.value] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() },
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

    private suspend fun persist(record: RadioStationPersistenceRecord) {
        stationDao.deleteFallbackStreams(record.station.stationId)
        stationDao.upsertStation(record.station)
        if (record.fallbackStreams.isNotEmpty()) {
            stationDao.upsertFallbackStreams(record.fallbackStreams)
        }
    }
}
