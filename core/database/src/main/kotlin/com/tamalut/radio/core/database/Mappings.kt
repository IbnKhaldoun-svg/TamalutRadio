package com.tamalut.radio.core.database

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaItemSummary
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.RecentlyPlayedEntry
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint

data class RadioStationPersistenceRecord(
    val station: RadioStationEntity,
    val fallbackStreams: List<RadioStationFallbackEntity>,
)

fun RadioStation.toPersistenceRecord(isCustom: Boolean): RadioStationPersistenceRecord =
    RadioStationPersistenceRecord(
        station = RadioStationEntity(
            stationId = id.value,
            name = name,
            primaryStreamUrl = primaryStream.url,
            isCustom = isCustom,
        ),
        fallbackStreams = fallbackStreams.mapIndexed { index, endpoint ->
            RadioStationFallbackEntity(
                stationId = id.value,
                position = index,
                url = endpoint.url,
            )
        },
    )

fun RadioStationWithFallbacks.toDomain(): RadioStation =
    RadioStation(
        id = StationId(station.stationId),
        name = station.name,
        primaryStream = StreamEndpoint(station.primaryStreamUrl),
        fallbackStreams = fallbackStreams
            .sortedBy(RadioStationFallbackEntity::position)
            .map { StreamEndpoint(it.url) },
    )

fun RecentlyPlayedEntry.toEntity(stationId: StationId? = null): RecentlyPlayedEntity =
    RecentlyPlayedEntity(
        mediaId = media.id.value,
        sourceType = media.sourceType.name,
        title = media.title,
        subtitle = media.subtitle,
        stationId = stationId?.value,
        playedAtEpochMillis = playedAtEpochMillis,
    )

fun RecentlyPlayedEntity.toDomainOrNull(): RecentlyPlayedEntry? {
    val decodedSourceType = runCatching { MediaSourceType.valueOf(sourceType) }.getOrNull() ?: return null
    return runCatching {
        RecentlyPlayedEntry(
            media = MediaItemSummary(
                id = MediaId(mediaId),
                title = title,
                subtitle = subtitle,
                sourceType = decodedSourceType,
            ),
            playedAtEpochMillis = playedAtEpochMillis,
        )
    }.getOrNull()
}
