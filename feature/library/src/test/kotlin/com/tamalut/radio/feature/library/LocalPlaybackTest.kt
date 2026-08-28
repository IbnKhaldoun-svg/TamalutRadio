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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlaybackTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun queuePreservesScannedOrderMetadataAndSelectedStartIndex() {
        val tracks = listOf(
            track("one", "One", "audio/mpeg"),
            track("two", "Two", "audio/flac"),
            track("three", "Three", null),
        )
        val queue = LocalPlaybackQueueFactory.create(tracks, tracks[1].id)
        assertEquals(1, queue.startIndex)
        assertEquals(tracks.map { it.id.value }, queue.items.map { it.mediaId.value })
        assertEquals("content://test/one", queue.items[0].contentUri)
        assertEquals("audio/mpeg", queue.items[0].mimeType)
        assertEquals("One", queue.items[0].title)
        assertEquals("Three", queue.items[2].title)
    }

    @Test
    fun viewModelDelegatesWholeQueueAndTracksSharedSessionTransitions() = runTest(dispatcher) {
        val tracks = listOf(track("one", "One"), track("two", "Two"), track("three", "Three"))
        val gateway = FakePlaybackGateway()
        val viewModel = createViewModel(tracks, gateway)
        advanceUntilIdle()

        viewModel.playTrack(tracks[1])
        advanceUntilIdle()
        assertEquals(tracks, gateway.lastTracks)
        assertEquals(tracks[1].id, gateway.lastSelectedId)
        assertEquals(tracks[1].id, viewModel.uiState.value.playingTrackId)
        assertTrue(viewModel.uiState.value.isLocalPlaybackActive)
        assertEquals("In riproduzione: Two", viewModel.uiState.value.playbackMessage)

        gateway.current.value = localState(tracks[2], repeat = PlaybackRepeatMode.ALL, shuffle = true)
        advanceUntilIdle()
        assertEquals(tracks[2].id, viewModel.uiState.value.playingTrackId)
        assertEquals(PlaybackRepeatMode.ALL, viewModel.uiState.value.repeatMode)
        assertTrue(viewModel.uiState.value.shuffleEnabled)
    }

    @Test
    fun switchingFromLocalToRadioClearsLocalPlayingMarkerAndModes() = runTest(dispatcher) {
        val tracks = listOf(track("one", "One"), track("two", "Two"))
        val gateway = FakePlaybackGateway()
        val viewModel = createViewModel(tracks, gateway)
        advanceUntilIdle()

        viewModel.playTrack(tracks[0])
        advanceUntilIdle()
        assertEquals(tracks[0].id, viewModel.uiState.value.playingTrackId)

        gateway.current.value = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.RADIO,
            stationId = StationId("radio-azawan"),
            title = "Radio Azawan",
            isPlaying = true,
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.playingTrackId)
        assertFalse(viewModel.uiState.value.isLocalPlaybackActive)
        assertEquals(PlaybackRepeatMode.OFF, viewModel.uiState.value.repeatMode)
        assertFalse(viewModel.uiState.value.shuffleEnabled)
        assertNull(viewModel.uiState.value.playbackMessage)
    }

    @Test
    fun repeatAndShuffleDelegateOnlyWhileLocalPlaybackIsActive() = runTest(dispatcher) {
        val tracks = listOf(track("one", "One"))
        val gateway = FakePlaybackGateway()
        val viewModel = createViewModel(tracks, gateway)
        advanceUntilIdle()

        viewModel.cycleRepeatMode()
        viewModel.toggleShuffle()
        assertTrue(gateway.repeatModes.isEmpty())
        assertTrue(gateway.shuffleValues.isEmpty())

        viewModel.playTrack(tracks.single())
        advanceUntilIdle()
        viewModel.cycleRepeatMode()
        viewModel.toggleShuffle()
        advanceUntilIdle()

        assertEquals(listOf(PlaybackRepeatMode.ALL), gateway.repeatModes)
        assertEquals(listOf(true), gateway.shuffleValues)
        assertEquals(PlaybackRepeatMode.ALL, viewModel.uiState.value.repeatMode)
        assertTrue(viewModel.uiState.value.shuffleEnabled)

        viewModel.cycleRepeatMode()
        advanceUntilIdle()
        assertEquals(listOf(PlaybackRepeatMode.ALL, PlaybackRepeatMode.ONE), gateway.repeatModes)
    }

    @Test
    fun refreshForcesAnotherScanOfThePersistedFolder() = runTest(dispatcher) {
        val tracks = listOf(track("one", "One"), track("two", "Two"))
        val scanner = FakeScanner(tracks)
        val viewModel = createViewModel(tracks, FakePlaybackGateway(), scanner)
        advanceUntilIdle()
        assertEquals(1, scanner.scanCalls)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, scanner.scanCalls)
        assertEquals(tracks, viewModel.uiState.value.tracks)
    }

    @Test
    fun playbackFailureIsRecoverableAndDoesNotDropScannedTracks() = runTest(dispatcher) {
        val tracks = listOf(track("one", "One"))
        val gateway = FakePlaybackGateway(Result.failure(IllegalStateException("controller failed")))
        val viewModel = createViewModel(tracks, gateway)
        advanceUntilIdle()
        viewModel.playTrack(tracks.single())
        assertEquals(tracks, viewModel.uiState.value.tracks)
        assertTrue(viewModel.uiState.value.playbackErrorMessage.orEmpty().contains("controller failed"))
    }

    private fun createViewModel(
        tracks: List<LocalAudioTrack>,
        gateway: FakePlaybackGateway,
        scanner: LocalAudioScanner = FakeScanner(tracks),
    ) = LibraryViewModel(
        preferencesRepository = FakePreferencesRepository(
            UserPreferences(localFolderUri = "content://test/tree/Music"),
        ),
        scanner = scanner,
        folderAccess = FakeFolderAccess,
        playbackGateway = gateway,
    )

    private fun track(id: String, title: String, mimeType: String? = "audio/mpeg") = LocalAudioTrack(
        id = MediaId(id),
        title = title,
        contentUri = "content://test/$id",
        mimeType = mimeType,
    )

    private fun localState(
        track: LocalAudioTrack,
        repeat: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
        shuffle: Boolean = false,
    ) = PlaybackState(
        isConnected = true,
        sourceType = MediaSourceType.LOCAL,
        mediaId = track.id,
        title = track.title,
        isPlaying = true,
        repeatMode = repeat,
        shuffleEnabled = shuffle,
    )

    private class FakePlaybackGateway(
        private val result: Result<Unit> = Result.success(Unit),
    ) : LocalPlaybackGateway {
        val current = MutableStateFlow(PlaybackState(isConnected = true))
        override val playbackState: StateFlow<PlaybackState> = current.asStateFlow()
        var lastTracks: List<LocalAudioTrack>? = null
        var lastSelectedId: MediaId? = null
        val repeatModes = mutableListOf<PlaybackRepeatMode>()
        val shuffleValues = mutableListOf<Boolean>()

        override fun play(
            tracks: List<LocalAudioTrack>,
            selectedTrackId: MediaId,
            onResult: (Result<Unit>) -> Unit,
        ) {
            lastTracks = tracks
            lastSelectedId = selectedTrackId
            if (result.isSuccess) {
                val selected = tracks.first { it.id == selectedTrackId }
                current.value = PlaybackState(
                    isConnected = true,
                    sourceType = MediaSourceType.LOCAL,
                    mediaId = selected.id,
                    title = selected.title,
                    isPlaying = true,
                )
            }
            onResult(result)
        }

        override fun setRepeatMode(mode: PlaybackRepeatMode) {
            repeatModes += mode
            current.value = current.value.copy(repeatMode = mode)
        }

        override fun setShuffleEnabled(enabled: Boolean) {
            shuffleValues += enabled
            current.value = current.value.copy(shuffleEnabled = enabled)
        }
    }

    private class FakeScanner(private val tracks: List<LocalAudioTrack>) : LocalAudioScanner {
        var scanCalls: Int = 0
            private set

        override suspend fun scan(treeUri: String): List<LocalAudioTrack> {
            scanCalls += 1
            return tracks
        }
    }

    private object FakeFolderAccess : LocalFolderAccess {
        override fun persistReadPermission(treeUri: String) = Unit
    }

    private class FakePreferencesRepository(initial: UserPreferences) : UserPreferencesRepository {
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
