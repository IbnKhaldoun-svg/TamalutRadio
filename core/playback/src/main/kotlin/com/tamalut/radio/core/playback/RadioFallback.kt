package com.tamalut.radio.core.playback

import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint

data class RadioFallbackConfig(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
    }
}

data class RadioFallbackPlan(
    val stationId: StationId,
    val stationName: String,
    val endpoints: List<StreamEndpoint>,
    val maxAttempts: Int,
    val currentIndex: Int,
) {
    init {
        require(endpoints.isNotEmpty()) { "Radio fallback plan requires at least one endpoint" }
        require(maxAttempts in 1..endpoints.size) { "maxAttempts must be bounded by available endpoints" }
        require(currentIndex in 0 until maxAttempts) { "currentIndex must be inside the attempt budget" }
    }

    val currentEndpoint: StreamEndpoint
        get() = endpoints[currentIndex]

    val attemptNumber: Int
        get() = currentIndex + 1

    fun onFatalError(errorCode: Int): RadioFallbackDecision {
        val nextIndex = currentIndex + 1
        return if (nextIndex < maxAttempts) {
            RadioFallbackDecision.Retry(copy(currentIndex = nextIndex))
        } else {
            RadioFallbackDecision.Exhausted(
                RadioFallbackState.Exhausted(
                    stationId = stationId,
                    attemptedCount = attemptNumber,
                    maxAttempts = maxAttempts,
                    lastErrorCode = errorCode,
                ),
            )
        }
    }

    fun asState(): RadioFallbackState.Attempting = RadioFallbackState.Attempting(
        stationId = stationId,
        endpointIndex = currentIndex,
        attemptedCount = attemptNumber,
        maxAttempts = maxAttempts,
    )

    companion object {
        fun fromStation(
            station: RadioStation,
            config: RadioFallbackConfig = RadioFallbackConfig(),
        ): RadioFallbackPlan {
            val endpoints = station.playbackStreams
            return RadioFallbackPlan(
                stationId = station.id,
                stationName = station.name,
                endpoints = endpoints,
                maxAttempts = minOf(config.maxAttempts, endpoints.size),
                currentIndex = 0,
            )
        }
    }
}

sealed interface RadioFallbackDecision {
    data class Retry(val plan: RadioFallbackPlan) : RadioFallbackDecision
    data class Exhausted(val state: RadioFallbackState.Exhausted) : RadioFallbackDecision
}

sealed interface RadioFallbackState {
    data object Inactive : RadioFallbackState

    data class Attempting(
        val stationId: StationId,
        val endpointIndex: Int,
        val attemptedCount: Int,
        val maxAttempts: Int,
    ) : RadioFallbackState

    data class Exhausted(
        val stationId: StationId,
        val attemptedCount: Int,
        val maxAttempts: Int,
        val lastErrorCode: Int,
    ) : RadioFallbackState
}
