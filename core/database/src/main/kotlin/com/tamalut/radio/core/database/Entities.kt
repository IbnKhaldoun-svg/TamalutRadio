package com.tamalut.radio.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey
    @ColumnInfo(name = "station_id")
    val stationId: String,
    val name: String,
    @ColumnInfo(name = "primary_stream_url")
    val primaryStreamUrl: String,
    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean,
)

@Entity(
    tableName = "radio_station_fallbacks",
    primaryKeys = ["station_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RadioStationEntity::class,
            parentColumns = ["station_id"],
            childColumns = ["station_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["station_id"]),
        Index(value = ["station_id", "url"], unique = true),
    ],
)
data class RadioStationFallbackEntity(
    @ColumnInfo(name = "station_id")
    val stationId: String,
    val position: Int,
    val url: String,
)

data class RadioStationWithFallbacks(
    @Embedded
    val station: RadioStationEntity,
    @Relation(
        parentColumns = ["station_id"],
        entityColumns = ["station_id"],
    )
    val fallbackStreams: List<RadioStationFallbackEntity>,
)

@Entity(
    tableName = "favorite_stations",
    foreignKeys = [
        ForeignKey(
            entity = RadioStationEntity::class,
            parentColumns = ["station_id"],
            childColumns = ["station_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["favorited_at_epoch_millis"])],
)
data class FavoriteStationEntity(
    @PrimaryKey
    @ColumnInfo(name = "station_id")
    val stationId: String,
    @ColumnInfo(name = "favorited_at_epoch_millis")
    val favoritedAtEpochMillis: Long,
)

@Entity(
    tableName = "recently_played",
    indices = [
        Index(value = ["played_at_epoch_millis"]),
        Index(value = ["station_id"]),
    ],
)
data class RecentlyPlayedEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_id")
    val mediaId: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    val title: String,
    val subtitle: String?,
    @ColumnInfo(name = "station_id")
    val stationId: String?,
    @ColumnInfo(name = "played_at_epoch_millis")
    val playedAtEpochMillis: Long,
)
