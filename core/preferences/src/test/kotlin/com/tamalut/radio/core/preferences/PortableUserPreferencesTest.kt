package com.tamalut.radio.core.preferences

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableUserPreferencesTest {
    @Test
    fun portableSnapshotContainsOnlyPortableValues() {
        val source = UserPreferences(
            themePreference = ThemePreference.DARK,
            languageTag = "it-IT",
            lastPlayed = LastPlayedPreference(
                sourceType = MediaSourceType.LOCAL,
                mediaId = MediaId("track-1"),
            ),
            localFolderUri = "content://music/private-folder",
            overlayEnabled = true,
            overlayEdge = OverlayEdge.LEFT,
            overlayVerticalFraction = 0.75f,
        )

        assertEquals(
            PortableUserPreferences(
                themePreference = ThemePreference.DARK,
                languageTag = "it-IT",
                overlayEnabled = true,
                overlayEdge = OverlayEdge.LEFT,
                overlayVerticalFraction = 0.75f,
            ),
            source.portableSnapshot(),
        )
    }

    @Test
    fun portableOverlayPositionMustBeFinite() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableUserPreferences(overlayVerticalFraction = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableUserPreferences(overlayVerticalFraction = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun dataStorePortableApplyUsesOneEditAndDoesNotTouchNonPortableKeys() {
        val source = Path.of(
            "src/main/kotlin/com/tamalut/radio/core/preferences/DataStoreUserPreferencesRepository.kt",
        ).readText()
        val method = source.substringAfter(
            "override suspend fun applyPortablePreferences(preferences: PortableUserPreferences)",
        ).substringBefore("\n}\n\ninternal fun decodeUserPreferences")

        assertEquals(1, Regex("dataStore\\.edit").findAll(method).count())
        assertTrue(method.contains("PreferenceKeys.themePreference"))
        assertTrue(method.contains("PreferenceKeys.languageTag"))
        assertTrue(method.contains("PreferenceKeys.overlayEnabled"))
        assertTrue(method.contains("PreferenceKeys.overlayEdge"))
        assertTrue(method.contains("PreferenceKeys.overlayVerticalFraction"))
        assertFalse(method.contains("PreferenceKeys.localFolderUri"))
        assertFalse(method.contains("PreferenceKeys.lastSourceType"))
        assertFalse(method.contains("PreferenceKeys.lastMediaId"))
        assertFalse(method.contains("PreferenceKeys.lastStationId"))
    }
}
