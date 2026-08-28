package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import com.tamalut.radio.core.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class RadioFeatureControllerTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun loadSeedsCatalogAndSortsStations() = runTest(dispatcher) {
        val source = FakeRadioDataSource(stations = mutableListOf(station("z", "Zulu"), station("a", "Alpha")))
        val snapshot = RadioFeatureController(source).load()
        assertEquals(1, source.seedCalls)
        assertEquals(listOf("Alpha", "Zulu"), snapshot.stations.map { it.name })
        assertTrue(snapshot.favoriteIds.isEmpty())
    }

    @Test
    fun toggleFavoriteAddsThenRemovesStation() = runTest(dispatcher) {
        val azawan = station("radio-azawan", "Radio Azawan")
        val source = FakeRadioDataSource(stations = mutableListOf(azawan))
        val controller = RadioFeatureController(source)
        assertTrue(azawan.id in controller.toggleFavorite(azawan.id, false).favoriteIds)
        assertFalse(azawan.id in controller.toggleFavorite(azawan.id, true).favoriteIds)
    }

    @Test
    fun favoritesSectionFiltersVisibleStationsAndSupportsEmptyState() {
        val one = station("one", "One")
        val two = station("two", "Two")
        val state = RadioUiState(
            isLoading = false,
            selectedSection = RadioSection.FAVORITES,
            stations = listOf(one, two),
            favoriteIds = setOf(two.id),
        )
        assertEquals(listOf(two), state.visibleStations)
        assertTrue(state.copy(favoriteIds = emptySet()).visibleStations.isEmpty())
        assertEquals(2, state.copy(selectedSection = RadioSection.ALL).visibleStations.size)
    }

    @Test
    fun viewModelExposesRecoverableErrorWhenRepositoryLoadFails() = runTest(dispatcher) {
        val viewModel = RadioViewModel(
            RadioFeatureController(FakeRadioDataSource(loadFailure = IllegalStateException("database unavailable"))),
            FakePlaybackGateway(),
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("database unavailable", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.stations.isEmpty())
    }

    @Test
    fun viewModelRefreshesFavoriteStateAfterMutation() = runTest(dispatcher) {
        val azawan = station("radio-azawan", "Radio Azawan")
        val source = FakeRadioDataSource(stations = mutableListOf(azawan))
        val viewModel = RadioViewModel(RadioFeatureController(source), FakePlaybackGateway())
        advanceUntilIdle()
        viewModel.toggleFavorite(azawan)
        advanceUntilIdle()
        assertTrue(azawan.id in viewModel.uiState.value.favoriteIds)
        assertEquals(1, source.favoriteMutations)
    }

    @Test
    fun stationSelectionDelegatesToPlaybackAndPreservesFavorites() = runTest(dispatcher) {
        val azawan = station("radio-azawan", "Radio Azawan")
        val source = FakeRadioDataSource(stations = mutableListOf(azawan), favorites = mutableSetOf(azawan.id))
        val playback = FakePlaybackGateway()
        val viewModel = RadioViewModel(RadioFeatureController(source), playback)
        advanceUntilIdle()
        viewModel.playStation(azawan)
        advanceUntilIdle()
        assertEquals(azawan, playback.lastStation)
        assertEquals(azawan.id, viewModel.uiState.value.playingStationId)
        assertEquals("In riproduzione: Radio Azawan", viewModel.uiState.value.playbackMessage)
        assertTrue(azawan.id in viewModel.uiState.value.favoriteIds)
        assertNull(viewModel.uiState.value.playbackErrorMessage)
    }

    @Test
    fun switchingFromRadioToLocalClearsStaleRadioPlayingMarker() = runTest(dispatcher) {
        val azawan = station("radio-azawan", "Radio Azawan")
        val playback = FakePlaybackGateway()
        val viewModel = RadioViewModel(
            RadioFeatureController(FakeRadioDataSource(stations = mutableListOf(azawan))),
            playback,
        )
        advanceUntilIdle()
        viewModel.playStation(azawan)
        advanceUntilIdle()
        assertEquals(azawan.id, viewModel.uiState.value.playingStationId)

        playback.current.value = PlaybackState(
            isConnected = true,
            sourceType = MediaSourceType.LOCAL,
            mediaId = MediaId("track-two"),
            title = "Track Two",
            isPlaying = true,
        )
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.playingStationId)
        assertNull(viewModel.uiState.value.playbackMessage)
    }

    @Test
    fun playbackFailureIsExposedWithoutDestroyingLoadedData() = runTest(dispatcher) {
        val azawan = station("radio-azawan", "Radio Azawan")
        val source = FakeRadioDataSource(stations = mutableListOf(azawan))
        val playback = FakePlaybackGateway(failure = IllegalStateException("service unavailable"))
        val viewModel = RadioViewModel(RadioFeatureController(source), playback)
        advanceUntilIdle()
        viewModel.playStation(azawan)
        assertEquals(listOf(azawan), viewModel.uiState.value.stations)
        assertNull(viewModel.uiState.value.playingStationId)
        assertTrue(viewModel.uiState.value.playbackErrorMessage!!.contains("service unavailable"))
    }

    private fun station(id: String, name: String) = RadioStation(
        id = StationId(id),
        name = name,
        primaryStream = StreamEndpoint("https://example.invalid/$id.mp3"),
    )

    private class FakePlaybackGateway(private val failure: Throwable? = null) : RadioPlaybackGateway {
        val current = MutableStateFlow(PlaybackState(isConnected = true))
        override val playbackState: StateFlow<PlaybackState> = current.asStateFlow()
        var lastStation: RadioStation? = null

        override fun play(station: RadioStation, onResult: (Result<Unit>) -> Unit) {
            lastStation = station
            if (failure == null) {
                current.value = PlaybackState(
                    isConnected = true,
                    sourceType = MediaSourceType.RADIO,
                    stationId = station.id,
                    title = station.name,
                    isPlaying = true,
                )
                onResult(Result.success(Unit))
            } else {
                onResult(Result.failure(failure))
            }
        }
    }

    private class FakeRadioDataSource(
        private val stations: MutableList<RadioStation> = mutableListOf(),
        private val favorites: MutableSet<StationId> = mutableSetOf(),
        private val loadFailure: Throwable? = null,
    ) : RadioDataSource {
        var seedCalls = 0
        var favoriteMutations = 0
        override suspend fun seedInitialCatalog() { seedCalls += 1; loadFailure?.let { throw it } }
        override suspend fun stations(): List<RadioStation> = stations.toList()
        override suspend fun favoriteIds(): Set<StationId> = favorites.toSet()
        override suspend fun setFavorite(stationId: StationId, favorite: Boolean) {
            favoriteMutations += 1
            if (favorite) favorites += stationId else favorites -= stationId
        }
    }
}
