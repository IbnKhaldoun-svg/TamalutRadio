package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.data.FavoriteStationRepository
import com.tamalut.radio.core.data.RadioStationRepository
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId

interface RadioDataSource {
    suspend fun seedInitialCatalog()
    suspend fun stations(): List<RadioStation>
    suspend fun favoriteIds(): Set<StationId>
    suspend fun setFavorite(stationId: StationId, favorite: Boolean)
}

class CoreRadioDataSource(
    private val stationRepository: RadioStationRepository,
    private val favoriteRepository: FavoriteStationRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : RadioDataSource {
    override suspend fun seedInitialCatalog() = stationRepository.seedInitialCatalog()

    override suspend fun stations(): List<RadioStation> =
        stationRepository.getStations(favoritesFirst = false)

    override suspend fun favoriteIds(): Set<StationId> =
        favoriteRepository.getFavoriteStationIds().toSet()

    override suspend fun setFavorite(stationId: StationId, favorite: Boolean) {
        if (favorite) {
            favoriteRepository.add(stationId, nowEpochMillis())
        } else {
            favoriteRepository.remove(stationId)
        }
    }
}

data class RadioSnapshot(
    val stations: List<RadioStation>,
    val favoriteIds: Set<StationId>,
)

class RadioFeatureController(
    private val dataSource: RadioDataSource,
) {
    suspend fun load(): RadioSnapshot {
        dataSource.seedInitialCatalog()
        return snapshot()
    }

    suspend fun toggleFavorite(stationId: StationId, currentlyFavorite: Boolean): RadioSnapshot {
        dataSource.setFavorite(stationId, favorite = !currentlyFavorite)
        return snapshot()
    }

    private suspend fun snapshot(): RadioSnapshot = RadioSnapshot(
        stations = dataSource.stations(),
        favoriteIds = dataSource.favoriteIds(),
    )
}
