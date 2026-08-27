package com.tamalut.radio.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
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
        MediaSourceType.LOCAL,
        MediaSourceType.DRIVE,
        -> mediaId?.let {
            LastPlayedPreference(
                sourceType = sourceType,
                mediaId = it,
                stationId = stationId,
            )
        }
        null -> null
    }

    return UserPreferences(
        themePreference = theme,
        languageTag = languageTag,
        lastPlayed = lastPlayed,
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
