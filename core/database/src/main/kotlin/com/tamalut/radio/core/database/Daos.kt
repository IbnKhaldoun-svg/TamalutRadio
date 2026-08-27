package com.tamalut.radio.core.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

@Dao
interface RadioStationDao {
    @Upsert
    suspend fun upsertStation(station: RadioStationEntity)

    @Upsert
    suspend fun upsertFallbackStreams(fallbackStreams: List<RadioStationFallbackEntity>)

    @Query("DELETE FROM radio_station_fallbacks WHERE station_id = :stationId")
    suspend fun deleteFallbackStreams(stationId: String)

    @Query("DELETE FROM radio_stations WHERE station_id = :stationId")
    suspend fun deleteStation(stationId: String)

    @Transaction
    @Query("SELECT * FROM radio_stations ORDER BY name COLLATE NOCASE")
    suspend fun getAllStations(): List<RadioStationWithFallbacks>

    @Transaction
    @Query("SELECT * FROM radio_stations WHERE is_custom = :isCustom ORDER BY name COLLATE NOCASE")
    suspend fun getStationsByCustomState(isCustom: Boolean): List<RadioStationWithFallbacks>

    @Transaction
    @Query("SELECT * FROM radio_stations WHERE station_id = :stationId LIMIT 1")
    suspend fun getStation(stationId: String): RadioStationWithFallbacks?
}

@Dao
interface FavoriteStationDao {
    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteStationEntity)

    @Query("DELETE FROM favorite_stations WHERE station_id = :stationId")
    suspend fun removeFavorite(stationId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_stations WHERE station_id = :stationId)")
    suspend fun isFavorite(stationId: String): Boolean

    @Query("SELECT station_id FROM favorite_stations ORDER BY favorited_at_epoch_millis DESC, station_id ASC")
    suspend fun getFavoriteStationIds(): List<String>
}

@Dao
interface RecentlyPlayedDao {
    @Upsert
    suspend fun upsert(entry: RecentlyPlayedEntity)

    @Query("SELECT * FROM recently_played ORDER BY played_at_epoch_millis DESC, media_id ASC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<RecentlyPlayedEntity>

    @Query("DELETE FROM recently_played WHERE media_id = :mediaId")
    suspend fun delete(mediaId: String)

    @Query(
        """
        DELETE FROM recently_played
        WHERE media_id NOT IN (
            SELECT media_id
            FROM recently_played
            ORDER BY played_at_epoch_millis DESC, media_id ASC
            LIMIT :limit
        )
        """,
    )
    suspend fun trimToLimit(limit: Int)

    @Query("DELETE FROM recently_played")
    suspend fun clear()
}
