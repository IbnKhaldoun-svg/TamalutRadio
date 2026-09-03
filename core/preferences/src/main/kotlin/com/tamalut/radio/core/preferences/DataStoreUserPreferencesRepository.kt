package com.tamalut.radio.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.StationId
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "user_preferences"

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME,
)

internal object PreferenceKeys {
    val themePreference = stringPreferencesKey("theme_preference")
    val languageTag = stringPreferencesKey("language_tag")
    val lastSourceType = stringPreferencesKey("last_source_type")
    val lastMediaId = stringPreferencesKey("last_media_id")
    val lastStationId = stringPreferencesKey("last_station_id")
    val localFolderUri = stringPreferencesKey("local_folder_uri")
    val overlayEnabled = booleanPreferencesKey("overlay_enabled")
    val overlayEdge = stringPreferencesKey("overlay_edge")
    val overlayVerticalFraction = floatPreferencesKey("overlay_vertical_fraction")
}

class DataStoreUserPreferencesRepository(
    context: Context,
) : UserPreferencesRepository {
    private val dataStore = context.applicationContext.userPreferencesDataStore

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(::decodeUserPreferences)

    override suspend fun setThemePreference(themePreference: ThemePreference) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.themePreference] = themePreference.name
        }
    }

    override suspend fun setLanguageTag(languageTag: String?) {
        dataStore.edit { preferences ->
            val normalized = languageTag?.trim()?.takeIf(String::isNotEmpty)
            if (normalized == null) {
                preferences.remove(PreferenceKeys.languageTag)
            } else {
                preferences[PreferenceKeys.languageTag] = normalized
            }
        }
    }

    override suspend fun setLastPlayed(
        sourceType: MediaSourceType,
        mediaId: MediaId?,
        stationId: StationId?,
    ) {
        LastPlayedPreference(
            sourceType = sourceType,
            mediaId = mediaId,
            stationId = stationId,
        )

        dataStore.edit { preferences ->
            preferences[PreferenceKeys.lastSourceType] = sourceType.name
            setOrRemove(preferences, PreferenceKeys.lastMediaId, mediaId?.value)
            setOrRemove(preferences, PreferenceKeys.lastStationId, stationId?.value)
        }
    }

    override suspend fun clearLastPlayed() {
        dataStore.edit { preferences ->
            preferences.remove(PreferenceKeys.lastSourceType)
            preferences.remove(PreferenceKeys.lastMediaId)
            preferences.remove(PreferenceKeys.lastStationId)
        }
    }

    override suspend fun setLocalFolderUri(localFolderUri: String?) {
        dataStore.edit { preferences ->
            setOrRemove(
                preferences = preferences,
                key = PreferenceKeys.localFolderUri,
                value = localFolderUri?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }

    override suspend fun setOverlayEnabled(overlayEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.overlayEnabled] = overlayEnabled
        }
    }

    override suspend fun setOverlayPosition(
        edge: OverlayEdge,
        verticalFraction: Float,
    ) {
        val normalizedFraction = verticalFraction
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: DEFAULT_OVERLAY_VERTICAL_FRACTION
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.overlayEdge] = edge.name
            preferences[PreferenceKeys.overlayVerticalFraction] = normalizedFraction
        }
    }

    override suspend fun applyPortablePreferences(preferences: PortableUserPreferences) {
        val normalizedLanguageTag = preferences.languageTag?.trim()?.takeIf(String::isNotEmpty)
        val normalizedFraction = preferences.overlayVerticalFraction
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: DEFAULT_OVERLAY_VERTICAL_FRACTION
        dataStore.edit { mutablePreferences ->
            mutablePreferences[PreferenceKeys.themePreference] = preferences.themePreference.name
            setOrRemove(mutablePreferences, PreferenceKeys.languageTag, normalizedLanguageTag)
            mutablePreferences[PreferenceKeys.overlayEnabled] = preferences.overlayEnabled
            mutablePreferences[PreferenceKeys.overlayEdge] = preferences.overlayEdge.name
            mutablePreferences[PreferenceKeys.overlayVerticalFraction] = normalizedFraction
        }
    }
}

internal fun decodeUserPreferences(preferences: Preferences): UserPreferences {
    val theme = ThemePreference.entries.firstOrNull {
        it.name == preferences[PreferenceKeys.themePreference]
    } ?: ThemePreference.FOLLOW_SYSTEM

    val languageTag = preferences[PreferenceKeys.languageTag]
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    val sourceType = MediaSourceType.entries.firstOrNull {
        it.name == preferences[PreferenceKeys.lastSourceType]
    }
    val mediaId = preferences[PreferenceKeys.lastMediaId]
        ?.takeIf(String::isNotBlank)
        ?.let(::MediaId)
    val stationId = preferences[PreferenceKeys.lastStationId]
        ?.takeIf(String::isNotBlank)
        ?.let(::StationId)

    val lastPlayed = when (sourceType) {
        MediaSourceType.RADIO -> stationId?.let {
            LastPlayedPreference(
                sourceType = MediaSourceType.RADIO,
                mediaId = mediaId,
                stationId = it,
            )
        }
        MediaSourceType.LOCAL -> mediaId?.let {
            LastPlayedPreference(
                sourceType = MediaSourceType.LOCAL,
                mediaId = it,
                stationId = stationId,
            )
        }
        null -> null
    }

    val localFolderUri = preferences[PreferenceKeys.localFolderUri]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val overlayEnabled = preferences[PreferenceKeys.overlayEnabled] ?: false
    val overlayEdge = OverlayEdge.entries.firstOrNull {
        it.name == preferences[PreferenceKeys.overlayEdge]
    } ?: OverlayEdge.RIGHT
    val overlayVerticalFraction = preferences[PreferenceKeys.overlayVerticalFraction]
        ?.takeIf(Float::isFinite)
        ?.coerceIn(0f, 1f)
        ?: DEFAULT_OVERLAY_VERTICAL_FRACTION

    return UserPreferences(
        themePreference = theme,
        languageTag = languageTag,
        lastPlayed = lastPlayed,
        localFolderUri = localFolderUri,
        overlayEnabled = overlayEnabled,
        overlayEdge = overlayEdge,
        overlayVerticalFraction = overlayVerticalFraction,
    )
}

private fun setOrRemove(
    preferences: androidx.datastore.preferences.core.MutablePreferences,
    key: Preferences.Key<String>,
    value: String?,
) {
    if (value == null) {
        preferences.remove(key)
    } else {
        preferences[key] = value
    }
}
