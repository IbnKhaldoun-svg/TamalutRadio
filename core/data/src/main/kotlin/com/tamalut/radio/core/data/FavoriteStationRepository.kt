package com.tamalut.radio.core.data

import com.tamalut.radio.core.database.FavoriteStationDao
import com.tamalut.radio.core.database.FavoriteStationEntity
import com.tamalut.radio.core.model.StationId

class FavoriteStationRepository(
    private val favoriteStationDao: FavoriteStationDao,
) {
    suspend fun add(stationId: StationId, favoritedAtEpochMillis: Long) {
        require(favoritedAtEpochMillis >= 0)
        favoriteStationDao.upsertFavorite(
            FavoriteStationEntity(
                stationId = stationId.value,
                favoritedAtEpochMillis = favoritedAtEpochMillis,
            ),
        )
    }

    suspend fun remove(stationId: StationId) {
        favoriteStationDao.removeFavorite(stationId.value)
    }

    suspend fun isFavorite(stationId: StationId): Boolean =
        favoriteStationDao.isFavorite(stationId.value)

    suspend fun getFavoriteStationIds(): List<StationId> =
        favoriteStationDao.getFavoriteStationIds().map(::StationId)
}
