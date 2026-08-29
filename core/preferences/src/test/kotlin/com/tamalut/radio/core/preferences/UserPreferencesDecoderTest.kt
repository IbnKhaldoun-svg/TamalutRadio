package com.tamalut.radio.core.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.tamalut.radio.core.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class UserPreferencesDecoderTest {
    @Test
    fun emptyPreferencesUseSafeDefaults() {
        val decoded = decodeUserPreferences(mutablePreferencesOf())

        assertEquals(ThemePreference.FOLLOW_SYSTEM, decoded.themePreference)
        assertNull(decoded.languageTag)
        assertNull(decoded.lastPlayed)
        assertNull(decoded.localFolderUri)
        assertFalse(decoded.overlayEnabled)
    }

    @Test
    fun unknownEnumsFallBackWithoutCrashing() {
        val preferences = mutablePreferencesOf(
            PreferenceKeys.themePreference to "NOT_A_THEME",
            PreferenceKeys.lastSourceType to "NOT_A_SOURCE",
            PreferenceKeys.lastMediaId to "media-1",
        )

        val decoded = decodeUserPreferences(preferences)

        assertEquals(ThemePreference.FOLLOW_SYSTEM, decoded.themePreference)
        assertNull(decoded.lastPlayed)
    }

    @Test
    fun validRadioPreferencesDecodeTypedIdentifiers() {
        val preferences = mutablePreferencesOf(
            PreferenceKeys.themePreference to ThemePreference.DARK.name,
            PreferenceKeys.languageTag to "it-IT",
            PreferenceKeys.lastSourceType to MediaSourceType.RADIO.name,
            PreferenceKeys.lastMediaId to "radio-media",
            PreferenceKeys.lastStationId to "radio-azawan",
            PreferenceKeys.localFolderUri to "content://com.example/tree/Music",
            PreferenceKeys.overlayEnabled to true,
        )

        val decoded = decodeUserPreferences(preferences)

        assertEquals(ThemePreference.DARK, decoded.themePreference)
        assertEquals("it-IT", decoded.languageTag)
        assertEquals(MediaSourceType.RADIO, decoded.lastPlayed?.sourceType)
        assertEquals("radio-media", decoded.lastPlayed?.mediaId?.value)
        assertEquals("radio-azawan", decoded.lastPlayed?.stationId?.value)
        assertEquals("content://com.example/tree/Music", decoded.localFolderUri)
        assertTrue(decoded.overlayEnabled)
    }

    @Test
    fun blankLocalFolderUriIsIgnored() {
        val decoded = decodeUserPreferences(
            mutablePreferencesOf(PreferenceKeys.localFolderUri to "   "),
        )

        assertNull(decoded.localFolderUri)
    }

    @Test
    fun incompleteLastPlayedStateIsIgnored() {
        val preferences = mutablePreferencesOf(
            PreferenceKeys.lastSourceType to MediaSourceType.LOCAL.name,
            PreferenceKeys.lastStationId to "irrelevant-station",
        )

        val decoded = decodeUserPreferences(preferences)

        assertNull(decoded.lastPlayed)
    }
}
