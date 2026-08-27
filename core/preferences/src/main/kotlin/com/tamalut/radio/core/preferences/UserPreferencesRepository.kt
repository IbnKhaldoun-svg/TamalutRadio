package com.tamalut.radio.core.preferences

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.StationId
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>

    suspend fun setThemePreference(themePreference: ThemePreference)

    suspend fun setLanguageTag(languageTag: String?)

    suspend fun setLastPlayed(
        sourceType: MediaSourceType,
        mediaId: MediaId? = null,
        stationId: StationId? = null,
    )

    suspend fun clearLastPlayed()

    suspend fun setLocalFolderUri(localFolderUri: String?)
}
