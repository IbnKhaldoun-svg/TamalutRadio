package com.tamalut.radio.core.model

data class StreamEndpoint(
    val url: String,
) {
    init {
        require(url.isNotBlank()) { "Stream endpoint URL must not be blank" }
    }
}

data class RadioStation(
    val id: StationId,
    val name: String,
    val primaryStream: StreamEndpoint,
    val fallbackStreams: List<StreamEndpoint> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "Radio station name must not be blank" }
        require(primaryStream !in fallbackStreams) {
            "Primary stream must not be duplicated in fallback streams"
        }
        require(fallbackStreams.distinct().size == fallbackStreams.size) {
            "Fallback streams must not contain duplicates"
        }
    }

    val playbackStreams: List<StreamEndpoint>
        get() = listOf(primaryStream) + fallbackStreams
}
