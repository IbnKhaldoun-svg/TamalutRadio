package com.tamalut.radio.feature.library

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.preferences.ThemePreference
import com.tamalut.radio.core.preferences.UserPreferences
import com.tamalut.radio.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun persistedFolderIsRestoredAndScannedAutomatically() = runTest(dispatcher) {
        val folderUri = "content://test/tree/Music"
        val track = track("one", "One")
        val preferences = FakePreferencesRepository(
            UserPreferences(localFolderUri = folderUri),
        )
        val scanner = FakeScanner(result = listOf(track))
        val viewModel = LibraryViewModel(preferences, scanner, FakeFolderAccess())

        advanceUntilIdle()

        assertEquals(listOf(folderUri), scanner.scans)
        assertEquals(folderUri, viewModel.uiState.value.folderUri)
        assertEquals(listOf(track), viewModel.uiState.value.tracks)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun selectingFolderPersistsPermissionPreferenceAndScans() = runTest(dispatcher) {
        val folderUri = "content://test/tree/NewMusic"
        val preferences = FakePreferencesRepository(UserPreferences())
        val scanner = FakeScanner(result = listOf(track("two", "Two")))
        val folderAccess = FakeFolderAccess()
        val viewModel = LibraryViewModel(preferences, scanner, folderAccess)
        advanceUntilIdle()

        viewModel.selectFolder(folderUri)
        advanceUntilIdle()

        assertEquals(listOf(folderUri), folderAccess.persistedUris)
        assertEquals(folderUri, preferences.state.value.localFolderUri)
        assertEquals(listOf(folderUri), scanner.scans)
        assertEquals("Two", viewModel.uiState.value.tracks.single().title)
    }

    @Test
    fun scanFailureIsExposedAsRecoverableUiError() = runTest(dispatcher) {
        val folderUri = "content://test/tree/Missing"
        val preferences = FakePreferencesRepository(
            UserPreferences(localFolderUri = folderUri),
        )
        val scanner = FakeScanner(failure = SecurityException("permission revoked"))
        val viewModel = LibraryViewModel(preferences, scanner, FakeFolderAccess())

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.tracks.isEmpty())
        assertEquals("permission revoked", viewModel.uiState.value.errorMessage)
    }

    private fun track(id: String, title: String) = LocalAudioTrack(
        id = MediaId(id),
        title = title,
        contentUri = "content://test/$id",
        mimeType = "audio/mpeg",
    )

    private class FakeScanner(
        private val result: List<LocalAudioTrack> = emptyList(),
        private val failure: Throwable? = null,
    ) : LocalAudioScanner {
        val scans = mutableListOf<String>()

        override suspend fun scan(treeUri: String): List<LocalAudioTrack> {
            scans += treeUri
            failure?.let { throw it }
            return result
        }
    }

    private class FakeFolderAccess : LocalFolderAccess {
        val persistedUris = mutableListOf<String>()

        override fun persistReadPermission(treeUri: String) {
            persistedUris += treeUri
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
        ) = Unit

        override suspend fun clearLastPlayed() = Unit

        override suspend fun setLocalFolderUri(localFolderUri: String?) {
            state.value = state.value.copy(localFolderUri = localFolderUri)
        }
    }
}
