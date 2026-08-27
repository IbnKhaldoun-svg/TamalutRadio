package com.tamalut.radio.core.model

enum class MediaSourceType {
    RADIO,
    LOCAL,
    DRIVE,
}

data class MediaItemSummary(
    val id: MediaId,
    val title: String,
    val subtitle: String? = null,
    val sourceType: MediaSourceType,
) {
    init {
        require(title.isNotBlank()) { "Media title must not be blank" }
    }
}

data class RecentlyPlayedEntry(
    val media: MediaItemSummary,
    val playedAtEpochMillis: Long,
) {
    init {
        require(playedAtEpochMillis >= 0L) { "playedAtEpochMillis must not be negative" }
    }
}
