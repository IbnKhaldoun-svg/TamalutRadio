package com.tamalut.radio

import com.tamalut.radio.core.database.FavoriteStationEntity
import com.tamalut.radio.core.database.RadioStationEntity
import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.preferences.LastPlayedPreference
import com.tamalut.radio.core.preferences.OverlayEdge
import com.tamalut.radio.core.preferences.PortableUserPreferences
import com.tamalut.radio.core.preferences.ThemePreference
import com.tamalut.radio.core.preferences.UserPreferences
import com.tamalut.radio.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreTest {
    @Test
    fun jsonV1RoundTripsAndIgnoresUnknownFields() {
        val original = BackupEnvelope(
            createdAtEpochMillis = 42L,
            customStations = listOf(
                BackupCustomStation(
                    stationId = "custom-jazz",
                    name = "Jazz One",
                    primaryStreamUrl = "https://radio.example/jazz.mp3",
                    category = "Jazz",
                ),
            ),
            favorites = listOf(BackupFavorite("custom-jazz", 123L)),
            portablePreferences = PortableUserPreferences(
                themePreference = ThemePreference.DARK,
                languageTag = "it-IT",
                overlayEnabled = true,
                overlayEdge = OverlayEdge.LEFT,
                overlayVerticalFraction = 0.72f,
            ),
        )

        assertEquals(original, BackupJsonCodec.decode(BackupJsonCodec.encode(original)))

        val withUnknownField = BackupJsonCodec.encode(original)
            .decodeToString()
            .replaceFirst("{", "{\"futureField\":{\"ignored\":true},")
            .encodeToByteArray()
        assertEquals(original, BackupJsonCodec.decode(withUnknownField))
    }

    @Test
    fun exportContainsOnlyCustomStationsFavoritesAndPortablePreferences() = runBlocking {
        val builtIn = builtIn("builtin-one", "https://built.example/one.mp3")
        val custom = custom("custom-one", "Custom One", "https://custom.example/one.mp3", "Jazz")
        val store = FakeBackupDataStore(
            customStations = listOf(custom),
            builtInStations = listOf(builtIn),
            favorites = listOf(
                FavoriteStationEntity("builtin-one", 50L),
                FavoriteStationEntity("custom-one", 40L),
            ),
        )
        val lastPlayed = LastPlayedPreference(
            sourceType = MediaSourceType.LOCAL,
            mediaId = MediaId("track-one"),
        )
        val preferences = FakePreferencesRepository(
            UserPreferences(
                themePreference = ThemePreference.DARK,
                languageTag = "it",
                lastPlayed = lastPlayed,
                localFolderUri = "content://music/folder",
                overlayEnabled = true,
                overlayEdge = OverlayEdge.LEFT,
                overlayVerticalFraction = 0.61f,
            ),
        )
        val coordinator = BackupRestoreCoordinator(
            dataStore = store,
            preferencesRepository = preferences,
            overlayPermissionGranted = { true },
            nowEpochMillis = { 999L },
        )

        val exportedText = coordinator.exportBytes().decodeToString()
        val envelope = BackupJsonCodec.decode(exportedText.encodeToByteArray())

        assertEquals(999L, envelope.createdAtEpochMillis)
        assertEquals(listOf("custom-one"), envelope.customStations.map { it.stationId })
        assertEquals(listOf("builtin-one", "custom-one"), envelope.favorites.map { it.stationId })
        assertEquals(ThemePreference.DARK, envelope.portablePreferences.themePreference)
        assertEquals("it", envelope.portablePreferences.languageTag)
        assertFalse(exportedText.contains("localFolderUri"))
        assertFalse(exportedText.contains("content://music/folder"))
        assertFalse(exportedText.contains("lastPlayed"))
        assertFalse(exportedText.contains("track-one"))
        assertFalse(exportedText.contains(builtIn.name))
    }

    @Test
    fun prepareImportNormalizesCategoriesSkipsUnknownBuiltInAndNeverNeedsNetwork() = runBlocking {
        val store = FakeBackupDataStore(
            builtInStations = listOf(
                builtIn("builtin-one", "https://built.example/one.mp3"),
            ),
        )
        val coordinator = BackupRestoreCoordinator(
            dataStore = store,
            preferencesRepository = FakePreferencesRepository(UserPreferences()),
            overlayPermissionGranted = { true },
        )
        val envelope = BackupEnvelope(
            createdAtEpochMillis = 1L,
            customStations = listOf(
                BackupCustomStation(
                    stationId = "custom-italia",
                    name = "  Italia Custom  ",
                    primaryStreamUrl = "HTTPS://CUSTOM.EXAMPLE:443/live/../italia.mp3",
                    category = "italia",
                ),
                BackupCustomStation(
                    stationId = "custom-jazz-a",
                    name = "Jazz A",
                    primaryStreamUrl = "https://custom.example/jazz-a.mp3",
                    category = " Jazz ",
                ),
                BackupCustomStation(
                    stationId = "custom-jazz-b",
                    name = "Jazz B",
                    primaryStreamUrl = "https://custom.example/jazz-b.mp3",
                    category = "jAZZ",
                ),
            ),
            favorites = listOf(
                BackupFavorite("builtin-one", 30L),
                BackupFavorite("custom-jazz-a", 20L),
                BackupFavorite("old-built-in", 10L),
            ),
            portablePreferences = PortableUserPreferences(),
        )

        val prepared = coordinator.prepareImport(BackupJsonCodec.encode(envelope))

        assertEquals(3, prepared.preview.customStationCount)
        assertEquals(2, prepared.preview.favoriteCount)
        assertEquals(1, prepared.preview.skippedUnknownBuiltInFavoriteCount)
        assertEquals("Italia Custom", prepared.customStations[0].name)
        assertEquals("https://custom.example/italia.mp3", prepared.customStations[0].primaryStreamUrl)
        assertEquals("Italia", prepared.customStations[0].customCategory)
        assertEquals("Jazz", prepared.customStations[1].customCategory)
        assertEquals("Jazz", prepared.customStations[2].customCategory)
        assertEquals(listOf("builtin-one", "custom-jazz-a"), prepared.favorites.map { it.stationId })
    }

    @Test
    fun malformedFutureOversizedDuplicateAndCollisionBackupsAreRejectedBeforeReplace() = runBlocking {
        val builtIn = builtIn("builtin-one", "https://built.example/one.mp3")
        val store = FakeBackupDataStore(builtInStations = listOf(builtIn))
        val coordinator = BackupRestoreCoordinator(
            dataStore = store,
            preferencesRepository = FakePreferencesRepository(UserPreferences()),
            overlayPermissionGranted = { true },
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.prepareImport("not-json".encodeToByteArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.prepareImport(ByteArray(MAX_BACKUP_BYTES + 1)) }
        }
        val future = sampleEnvelope().copy(formatVersion = BACKUP_FORMAT_VERSION + 1)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.prepareImport(BackupJsonCodec.encode(future)) }
        }
        val duplicateId = sampleEnvelope().copy(
            customStations = listOf(
                customBackup("custom-x", "https://custom.example/x.mp3"),
                customBackup("custom-x", "https://custom.example/y.mp3"),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.prepareImport(BackupJsonCodec.encode(duplicateId)) }
        }
        val duplicateUrl = sampleEnvelope().copy(
            customStations = listOf(
                customBackup("custom-x", "https://custom.example/same.mp3"),
                customBackup("custom-y", "HTTPS://CUSTOM.EXAMPLE:443/same.mp3"),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.prepareImport(BackupJsonCodec.encode(duplicateUrl)) }
        }
        val builtInIdCollision = sampleEnvelope().copy(
            customStations = listOf(customBackup("builtin-one", "https://custom.example/x.mp3")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.prepareImport(BackupJsonCodec.encode(builtInIdCollision)) }
        }
        val builtInUrlCollision = sampleEnvelope().copy(
            customStations = listOf(customBackup("custom-x", builtIn.primaryStreamUrl)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.prepareImport(BackupJsonCodec.encode(builtInUrlCollision)) }
        }
        val missingCustomFavorite = sampleEnvelope().copy(
            favorites = listOf(BackupFavorite("custom-missing", 10L)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.prepareImport(BackupJsonCodec.encode(missingCustomFavorite)) }
        }

        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun restoreReplacesIdempotentlyPreservesNonPortablePrefsAndDowngradesOverlayPermission() = runBlocking {
        val existingLastPlayed = LastPlayedPreference(
            sourceType = MediaSourceType.RADIO,
            stationId = StationId("builtin-one"),
        )
        val preferences = FakePreferencesRepository(
            UserPreferences(
                themePreference = ThemePreference.LIGHT,
                languageTag = "en",
                lastPlayed = existingLastPlayed,
                localFolderUri = "content://music/keep-me",
                overlayEnabled = false,
                overlayEdge = OverlayEdge.RIGHT,
                overlayVerticalFraction = 0.3f,
            ),
        )
        val store = FakeBackupDataStore(
            customStations = listOf(custom("custom-old", "Old", "https://old.example/live.mp3", "Old")),
            builtInStations = listOf(builtIn("builtin-one", "https://built.example/one.mp3")),
            favorites = listOf(FavoriteStationEntity("builtin-one", 1L)),
        )
        val coordinator = BackupRestoreCoordinator(
            dataStore = store,
            preferencesRepository = preferences,
            overlayPermissionGranted = { false },
        )
        val prepared = coordinator.prepareImport(
            BackupJsonCodec.encode(
                BackupEnvelope(
                    createdAtEpochMillis = 10L,
                    customStations = listOf(
                        customBackup("custom-new", "https://new.example/live.mp3", category = "Jazz"),
                    ),
                    favorites = listOf(
                        BackupFavorite("custom-new", 88L),
                        BackupFavorite("builtin-one", 77L),
                    ),
                    portablePreferences = PortableUserPreferences(
                        themePreference = ThemePreference.DARK,
                        languageTag = "it",
                        overlayEnabled = true,
                        overlayEdge = OverlayEdge.LEFT,
                        overlayVerticalFraction = 0.8f,
                    ),
                ),
            ),
        )

        val first = coordinator.restore(prepared)
        val second = coordinator.restore(prepared)

        assertEquals(2, store.replaceCalls)
        assertEquals(listOf("custom-new"), store.customStations.map { it.stationId })
        assertEquals(listOf("custom-new", "builtin-one"), store.favorites.map { it.stationId })
        assertEquals(listOf(88L, 77L), store.favorites.map { it.favoritedAtEpochMillis })
        assertEquals(first, second)
        assertTrue(first.overlayPermissionRequired)

        val restoredPreferences = preferences.state.value
        assertEquals(ThemePreference.DARK, restoredPreferences.themePreference)
        assertEquals("it", restoredPreferences.languageTag)
        assertFalse(restoredPreferences.overlayEnabled)
        assertEquals(OverlayEdge.LEFT, restoredPreferences.overlayEdge)
        assertEquals(0.8f, restoredPreferences.overlayVerticalFraction)
        assertEquals("content://music/keep-me", restoredPreferences.localFolderUri)
        assertEquals(existingLastPlayed, restoredPreferences.lastPlayed)
    }

    private fun sampleEnvelope(): BackupEnvelope = BackupEnvelope(
        createdAtEpochMillis = 1L,
        customStations = listOf(customBackup("custom-one", "https://custom.example/one.mp3")),
        favorites = emptyList(),
        portablePreferences = PortableUserPreferences(),
    )

    private fun customBackup(
        id: String,
        url: String,
        category: String = "Jazz",
    ) = BackupCustomStation(
        stationId = id,
        name = "Station $id",
        primaryStreamUrl = url,
        category = category,
    )

    private fun builtIn(id: String, url: String) = RadioStationEntity(
        stationId = id,
        name = "Built-in $id",
        primaryStreamUrl = url,
        isCustom = false,
        customCategory = null,
    )

    private fun custom(id: String, name: String, url: String, category: String) = RadioStationEntity(
        stationId = id,
        name = name,
        primaryStreamUrl = url,
        isCustom = true,
        customCategory = category,
    )
}

private class FakeBackupDataStore(
    customStations: List<RadioStationEntity> = emptyList(),
    builtInStations: List<RadioStationEntity> = emptyList(),
    favorites: List<FavoriteStationEntity> = emptyList(),
) : BackupDataStore {
    var customStations: List<RadioStationEntity> = customStations
        private set
    var builtInStations: List<RadioStationEntity> = builtInStations
        private set
    var favorites: List<FavoriteStationEntity> = favorites
        private set
    var replaceCalls: Int = 0
        private set

    override suspend fun ensureReady() = Unit
    override suspend fun customStations(): List<RadioStationEntity> = customStations
    override suspend fun builtInStations(): List<RadioStationEntity> = builtInStations
    override suspend fun favorites(): List<FavoriteStationEntity> = favorites

    override suspend fun replaceBackupManagedData(
        customStations: List<RadioStationEntity>,
        favorites: List<FavoriteStationEntity>,
    ) {
        replaceCalls += 1
        this.customStations = customStations.toList()
        this.favorites = favorites.toList()
    }
}

private class FakePreferencesRepository(
    initial: UserPreferences,
) : UserPreferencesRepository {
    val state = MutableStateFlow(initial)
    override val userPreferences: Flow<UserPreferences> = state

    override suspend fun setThemePreference(themePreference: ThemePreference) {
        state.value = state.value.copy(themePreference = themePreference)
    }

    override suspend fun setLanguageTag(languageTag: String?) {
        state.value = state.value.copy(languageTag = languageTag)
    }

    override suspend fun setLastPlayed(
        sourceType: MediaSourceType,
        mediaId: MediaId?,
        stationId: StationId?,
    ) {
        state.value = state.value.copy(
            lastPlayed = LastPlayedPreference(sourceType, mediaId, stationId),
        )
    }

    override suspend fun clearLastPlayed() {
        state.value = state.value.copy(lastPlayed = null)
    }

    override suspend fun setLocalFolderUri(localFolderUri: String?) {
        state.value = state.value.copy(localFolderUri = localFolderUri)
    }

    override suspend fun setOverlayEnabled(overlayEnabled: Boolean) {
        state.value = state.value.copy(overlayEnabled = overlayEnabled)
    }

    override suspend fun setOverlayPosition(edge: OverlayEdge, verticalFraction: Float) {
        state.value = state.value.copy(
            overlayEdge = edge,
            overlayVerticalFraction = verticalFraction,
        )
    }

    override suspend fun applyPortablePreferences(preferences: PortableUserPreferences) {
        state.value = state.value.copy(
            themePreference = preferences.themePreference,
            languageTag = preferences.languageTag,
            overlayEnabled = preferences.overlayEnabled,
            overlayEdge = preferences.overlayEdge,
            overlayVerticalFraction = preferences.overlayVerticalFraction,
        )
    }
}
