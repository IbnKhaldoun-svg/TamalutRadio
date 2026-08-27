package com.tamalut.radio.core.preferences

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.StationId

enum class ThemePreference {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

data class LastPlayedPreference(
    val sourceType: MediaSourceType,
    val mediaId: MediaId? = null,
    val stationId: StationId? = null,
) {
    init {
        when (sourceType) {
            MediaSourceType.RADIO -> require(stationId != null) {
                "A radio last-played preference requires stationId"
            }
            MediaSourceType.LOCAL,
            MediaSourceType.DRIVE,
            -> require(mediaId != null) {
                "A track last-played preference requires mediaId"
            }
        }
    }
}

data class UserPreferences(
    val themePreference: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    val languageTag: String? = null,
    val lastPlayed: LastPlayedPreference? = null,
)
