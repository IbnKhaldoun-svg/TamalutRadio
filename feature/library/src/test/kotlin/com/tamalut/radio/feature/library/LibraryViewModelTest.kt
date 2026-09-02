package com.tamalut.radio.feature.library

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.playback.PlaybackRepeatMode
import com.tamalut.radio.core.playback.PlaybackState
import com.tamalut.radio.core.preferences.ThemePreference
import com.tamalut.radio.core.preferences.UserPreferences
import com.tamalut.radio.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    @Test
    fun searchIsTrimmedCaseInsensitiveSubstringAndZeroResultIsNotEmptyLibrary() {
        val one = track("one", "Estate 2025")
        val two = track("two", "Notte Blu")
        val state = LibraryUiState(
            folderUri = "content://test/tree/Music",
            isLoading = false,
            tracks = listOf(one, two),
            searchQuery = "  eStAtE  ",
            isSearchOpen = true,
        )
        assertEquals(listOf(one), state.visibleTracks)

        val noMatch = state.copy(searchQuery = "inesistente")
        assertEquals(2, noMatch.tracks.size)
        assertTrue(noMatch.visibleTracks.isEmpty())

        val emptyLibrary = noMatch.copy(tracks = emptyList())
        assertTrue(emptyLibrary.tracks.isEmpty())
        assertTrue(emptyLibrary.visibleTracks.isEmpty())
    }

    @Test
    fun refreshPreservesSearchButFolderChangeClearsItAndCloseClearsQuery() = runTest(dispatcher) {
        val firstFolder = "content://test/tree/Music"
        val secondFolder = "content://test/tree/Other"
        val preferences = FakePreferencesRepository(UserPreferences(localFolderUri = firstFolder))
        val scanner = FakeScanner(result = listOf(track("one", "Estate 2025")))
        val viewModel = LibraryViewModel(preferences, scanner, FakeFolderAccess())
        advanceUntilIdle()

        viewModel.openSearch()
        viewModel.updateSearchQuery("estate")
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSearchOpen)
        assertEquals("estate", viewModel.uiState.value.searchQuery)

        viewModel.clearSearch()
        assertTrue(viewModel.uiState.value.isSearchOpen)
        assertEquals("", viewModel.uiState.value.searchQuery)
        viewModel.updateSearchQuery("estate")
        viewModel.closeSearch()
        assertFalse(viewModel.uiState.value.isSearchOpen)
        assertEquals("", viewModel.uiState.value.searchQuery)

        viewModel.openSearch()
        viewModel.updateSearchQuery("estate")
        viewModel.selectFolder(secondFolder)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSearchOpen)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(secondFolder, viewModel.uiState.value.folderUri)
    }

    @Test
    fun oneVisibleSearchResultStillPlaysCompleteScannedLibrary() = runTest(dispatcher) {
        val folder = "content://test/tree/Music"
        val alpha = track("alpha", "Alpha")
        val beta = track("beta", "Beta Song")
        val gamma = track("gamma", "Gamma")
        val allTracks = listOf(alpha, beta, gamma)
        val playback = FakePlaybackGateway()
        val viewModel = LibraryViewModel(
            FakePreferencesRepository(UserPreferences(localFolderUri = folder)),
            FakeScanner(result = allTracks),
            FakeFolderAccess(),
            playback,
        )
        advanceUntilIdle()

        viewModel.openSearch()
        viewModel.updateSearchQuery("beta")
        assertEquals(listOf(beta), viewModel.uiState.value.visibleTracks)

        viewModel.playTrack(beta)
        advanceUntilIdle()

        assertEquals(allTracks, playback.lastTracks)
        assertEquals(beta.id, playback.lastSelectedTrackId)
        assertFalse(viewModel.uiState.value.isSearchOpen)
        assertEquals("", viewModel.uiState.value.searchQuery)
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

    private class FakePlaybackGateway : LocalPlaybackGateway {
        private val current = MutableStateFlow(PlaybackState(isConnected = true))
        override val playbackState: StateFlow<PlaybackState> = current.asStateFlow()
        var lastTracks: List<LocalAudioTrack> = emptyList()
        var lastSelectedTrackId: MediaId? = null

        override fun play(
            tracks: List<LocalAudioTrack>,
            selectedTrackId: MediaId,
            onResult: (Result<Unit>) -> Unit,
        ) {
            lastTracks = tracks.toList()
            lastSelectedTrackId = selectedTrackId
            onResult(Result.success(Unit))
        }

        override fun setRepeatMode(mode: PlaybackRepeatMode) = Unit
        override fun setShuffleEnabled(enabled: Boolean) = Unit
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
