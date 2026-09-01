package com.tamalut.radio.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint

internal object RadioStreamMimeTypePolicy {
    private const val CHADA_STATION_ID = "chada-fm"
    private const val CHADA_HLS_ENDPOINT = "https://stream.bodkas.com/playlist"

    fun mimeTypeFor(stationId: StationId, endpoint: StreamEndpoint): String? {
        val normalizedUrl = endpoint.url.substringBefore('#')
        val pathWithoutQuery = normalizedUrl.substringBefore('?')
        return when {
            pathWithoutQuery.endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            stationId.value == CHADA_STATION_ID && normalizedUrl.startsWith(CHADA_HLS_ENDPOINT) ->
                MimeTypes.APPLICATION_M3U8
            else -> null
        }
    }
}

object RadioMediaItemFactory {
    private const val EXTRA_IS_TAMALUT_RADIO = "com.tamalut.radio.playback.IS_RADIO"
    private const val EXTRA_STATION_ID = "com.tamalut.radio.playback.STATION_ID"
    private const val EXTRA_ENDPOINTS = "com.tamalut.radio.playback.ENDPOINTS"
    private const val EXTRA_MAX_ATTEMPTS = "com.tamalut.radio.playback.MAX_ATTEMPTS"
    private const val EXTRA_CURRENT_INDEX = "com.tamalut.radio.playback.CURRENT_INDEX"

    fun create(
        station: com.tamalut.radio.core.model.RadioStation,
        config: RadioFallbackConfig = RadioFallbackConfig(),
    ): MediaItem = create(RadioFallbackPlan.fromStation(station, config))

    internal fun create(plan: RadioFallbackPlan): MediaItem {
        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_TAMALUT_RADIO, true)
            putString(EXTRA_STATION_ID, plan.stationId.value)
            putStringArrayList(EXTRA_ENDPOINTS, ArrayList(plan.endpoints.map(StreamEndpoint::url)))
            putInt(EXTRA_MAX_ATTEMPTS, plan.maxAttempts)
            putInt(EXTRA_CURRENT_INDEX, plan.currentIndex)
        }
        val builder = MediaItem.Builder()
            .setMediaId("radio:${plan.stationId.value}")
            .setUri(plan.currentEndpoint.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(plan.stationName)
                    .setExtras(extras)
                    .build(),
            )
        RadioStreamMimeTypePolicy.mimeTypeFor(plan.stationId, plan.currentEndpoint)
            ?.let(builder::setMimeType)
        return builder.build()
    }

    internal fun planFrom(mediaItem: MediaItem?): RadioFallbackPlan? {
        val item = mediaItem ?: return null
        val extras = item.mediaMetadata.extras ?: return null
        if (!extras.getBoolean(EXTRA_IS_TAMALUT_RADIO, false)) return null

        return runCatching {
            val stationId = StationId(requireNotNull(extras.getString(EXTRA_STATION_ID)))
            val urls = requireNotNull(extras.getStringArrayList(EXTRA_ENDPOINTS))
            val endpoints = urls.map(::StreamEndpoint)
            val maxAttempts = extras.getInt(EXTRA_MAX_ATTEMPTS)
            val currentIndex = extras.getInt(EXTRA_CURRENT_INDEX)
            val stationName = item.mediaMetadata.title?.toString().orEmpty().ifBlank { stationId.value }
            RadioFallbackPlan(
                stationId = stationId,
                stationName = stationName,
                endpoints = endpoints,
                maxAttempts = maxAttempts,
                currentIndex = currentIndex,
            )
        }.getOrNull()
    }
}
