package com.tamalut.radio.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        RadioStationEntity::class,
        RadioStationFallbackEntity::class,
        FavoriteStationEntity::class,
        RecentlyPlayedEntity::class,
    ],
    version = TAMALUT_DATABASE_VERSION,
    exportSchema = true,
)
abstract class TamalutDatabase : RoomDatabase() {
    abstract fun radioStationDao(): RadioStationDao
    abstract fun favoriteStationDao(): FavoriteStationDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
}
