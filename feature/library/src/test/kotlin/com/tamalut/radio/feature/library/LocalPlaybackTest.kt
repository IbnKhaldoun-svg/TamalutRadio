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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlaybackTest {
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
    fun queuePreservesScannedOrderMetadataAndSelectedStartIndex() {
        val tracks = listOf(
            track("one", "One", "audio/mpeg"),
            track("two", "Two", "audio/flac"),
            track("three", "Three", null),
        )

        val queue = LocalPlaybackQueueFactory.create(tracks, tracks[1].id)

        assertEquals(1, queue.startIndex)
        assertEquals(tracks.map { it.id.value }, queue.items.map { it.mediaId })
        assertEquals("content://test/one", queue.items[0].contentUri)
        assertEquals("audio/mpeg", queue.items[0].mimeType)
        assertEquals("One", queue.items[0].title)
        assertEquals("Three", queue.items[2].title)
    }

    @Test
    fun viewModelDelegatesWholeQueueAndTracksSessionTransitions() = runTest(dispatcher) {
        val folderUri = "content://test/tree/Music"
        val tracks = listOf(
            track("one", "One"),
            track("two", "Two"),
            track("three", "Three"),
        )
        val gateway = FakePlaybackGateway()
        val viewModel = LibraryViewModel(
            preferencesRepository = FakePreferencesRepository(UserPreferences(localFolderUri = folderUri)),
            scanner = FakeScanner(tracks),
            folderAccess = FakeFolderAccess,
            playbackGateway = gateway,
        )
        advanceUntilIdle()

        viewModel.playTrack(tracks[1])

        assertEquals(tracks, gateway.lastTracks)
        assertEquals(tracks[1].id, gateway.lastSelectedId)
        assertEquals(tracks[1].id, viewModel.uiState.value.playingTrackId)
        assertEquals("In riproduzione: Two", viewModel.uiState.value.playbackMessage)

        gateway.current.value = tracks[2].id
        advanceUntilIdle()
        assertEquals(tracks[2].id, viewModel.uiState.value.playingTrackId)

        gateway.current.value = MediaId("radio:test-station")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.playingTrackId)
    }

    @Test
    fun playbackFailureIsRecoverableAndDoesNotDropScannedTracks() = runTest(dispatcher) {
        val tracks = listOf(track("one", "One"))
        val gateway = FakePlaybackGateway(Result.failure(IllegalStateException("controller failed")))
        val viewModel = LibraryViewModel(
            preferencesRepository = FakePreferencesRepository(UserPreferences(localFolderUri = "content://test/tree/Music")),
            scanner = FakeScanner(tracks),
            folderAccess = FakeFolderAccess,
            playbackGateway = gateway,
        )
        advanceUntilIdle()

        viewModel.playTrack(tracks.single())

        assertEquals(tracks, viewModel.uiState.value.tracks)
        assertTrue(viewModel.uiState.value.playbackErrorMessage.orEmpty().contains("controller failed"))
    }

    private fun track(
        id: String,
        title: String,
        mimeType: String? = "audio/mpeg",
    ) = LocalAudioTrack(
        id = MediaId(id),
        title = title,
        contentUri = "content://test/$id",
        mimeType = mimeType,
    )

    private class FakePlaybackGateway(
        private val result: Result<Unit> = Result.success(Unit),
    ) : LocalPlaybackGateway {
        val current = MutableStateFlow<MediaId?>(null)
        override val currentMediaId: StateFlow<MediaId?> = current.asStateFlow()
        var lastTracks: List<LocalAudioTrack>? = null
        var lastSelectedId: MediaId? = null

        override fun play(
            tracks: List<LocalAudioTrack>,
            selectedTrackId: MediaId,
            onResult: (Result<Unit>) -> Unit,
        ) {
            lastTracks = tracks
            lastSelectedId = selectedTrackId
            if (result.isSuccess) {
                current.value = selectedTrackId
            }
            onResult(result)
        }
    }

    private class FakeScanner(
        private val tracks: List<LocalAudioTrack>,
    ) : LocalAudioScanner {
        override suspend fun scan(treeUri: String): List<LocalAudioTrack> = tracks
    }

    private object FakeFolderAccess : LocalFolderAccess {
        override fun persistReadPermission(treeUri: String) = Unit
    }

    private class FakePreferencesRepository(
        initial: UserPreferences,
    ) : UserPreferencesRepository {
        private val state = MutableStateFlow(initial)
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
