package com.tamalut.radio.core.preferences

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.StationId

enum class ThemePreference {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

enum class OverlayEdge {
    LEFT,
    RIGHT,
}

const val DEFAULT_OVERLAY_VERTICAL_FRACTION = 0.35f

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
            MediaSourceType.LOCAL -> require(mediaId != null) {
                "A track last-played preference requires mediaId"
            }
        }
    }
}

data class UserPreferences(
    val themePreference: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    val languageTag: String? = null,
    val lastPlayed: LastPlayedPreference? = null,
    val localFolderUri: String? = null,
    val overlayEnabled: Boolean = false,
    val overlayEdge: OverlayEdge = OverlayEdge.RIGHT,
    val overlayVerticalFraction: Float = DEFAULT_OVERLAY_VERTICAL_FRACTION,
)
