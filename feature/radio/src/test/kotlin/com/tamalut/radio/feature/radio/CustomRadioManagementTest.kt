package com.tamalut.radio.feature.radio

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
class CustomRadioManagementTest {
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
    fun addTrimsFieldsValidatesHttpsAndCreatesCustomPrefixedId() = runTest(dispatcher) {
        val source = MutableCustomRadioDataSource()
        val validator = RecordingValidator()
        val controller = RadioFeatureController(
            dataSource = source,
            streamValidator = validator,
            customStationIdFactory = { StationId("custom-fixed") },
        )

        val result = controller.saveCustomStation(
            stationId = null,
            name = "  Mia Radio  ",
            streamUrl = " HTTPS://Example.COM:443/live ",
        )

        assertEquals(listOf("https://example.com/live"), validator.urls)
        assertEquals(setOf(StationId("custom-fixed")), result.customStationIds)
        val saved = result.stations.single()
        assertEquals("Mia Radio", saved.name)
        assertEquals("https://example.com/live", saved.primaryStream.url)
        assertTrue(saved.id.value.startsWith("custom-"))
    }

    @Test
    fun duplicateNormalizedPrimaryUrlIsRejectedBeforeNetworkProbe() = runTest(dispatcher) {
        val builtIn = station("built-in", "Built in", "https://example.com/live")
        val source = MutableCustomRadioDataSource(stations = mutableListOf(builtIn))
        val validator = RecordingValidator()
        val controller = RadioFeatureController(
            source,
            validator,
            customStationIdFactory = { StationId("custom-fixed") },
        )

        val error = captureFailure {
            controller.saveCustomStation(
                stationId = null,
                name = "Duplicate",
                streamUrl = "HTTPS://EXAMPLE.COM:443/live",
            )
        }

        assertTrue(error.message.orEmpty().contains("già presente"))
        assertTrue(validator.urls.isEmpty())
        assertEquals(listOf(builtIn), source.stations())
    }

    @Test
    fun editPreservesCustomIdAndFavoriteAndBuiltInEditIsRejected() = runTest(dispatcher) {
        val builtIn = station("built-in", "Built in", "https://example.com/built")
        val custom = station("custom-one", "Old", "https://example.com/old")
        val source = MutableCustomRadioDataSource(
            stations = mutableListOf(builtIn, custom),
            favorites = mutableSetOf(custom.id),
            customIds = mutableSetOf(custom.id),
        )
        val controller = RadioFeatureController(source, RecordingValidator())

        val result = controller.saveCustomStation(
            stationId = custom.id,
            name = "New Name",
            streamUrl = "https://example.com/new",
        )
        assertEquals(custom.id, result.stations.first { it.id == custom.id }.id)
        assertEquals("New Name", result.stations.first { it.id == custom.id }.name)
        assertTrue(custom.id in result.favoriteIds)

        val error = captureFailure {
            controller.saveCustomStation(
                stationId = builtIn.id,
                name = "No",
                streamUrl = "https://example.com/no",
            )
        }
        assertTrue(error.message.orEmpty().contains("integrate"))
        assertEquals("Built in", source.stations().first { it.id == builtIn.id }.name)
    }

    @Test
    fun deleteRemovesOnlyCustomStationAndFavorite() = runTest(dispatcher) {
        val builtIn = station("built-in", "Built in", "https://example.com/built")
        val custom = station("custom-one", "Custom", "https://example.com/custom")
        val source = MutableCustomRadioDataSource(
            stations = mutableListOf(builtIn, custom),
            favorites = mutableSetOf(custom.id),
            customIds = mutableSetOf(custom.id),
        )
        val controller = RadioFeatureController(source, RecordingValidator())

        val result = controller.deleteCustomStation(custom.id)
        assertEquals(listOf(builtIn), result.stations)
        assertFalse(custom.id in result.favoriteIds)
        assertFalse(custom.id in result.customStationIds)

        val error = captureFailure { controller.deleteCustomStation(builtIn.id) }
        assertTrue(error.message.orEmpty().contains("integrate"))
        assertEquals(listOf(builtIn), source.stations())
    }

    @Test
    fun personalSearchResultStillLaunchesCompleteCustomContextQueue() = runTest(dispatcher) {
        val builtIn = station("built-in", "Built in", "https://example.com/built")
        val customA = station("custom-a", "Alpha Personal", "https://example.com/a")
        val customB = station("custom-b", "Beta Personal", "https://example.com/b")
        val source = MutableCustomRadioDataSource(
            stations = mutableListOf(builtIn, customA, customB),
            customIds = mutableSetOf(customA.id, customB.id),
        )
        val playback = CustomTestPlaybackGateway()
        val viewModel = RadioViewModel(
            RadioFeatureController(source, RecordingValidator()),
            playback,
        )
        advanceUntilIdle()

        viewModel.selectFilter(RadioStationFilter.PERSONAL)
        viewModel.openSearch()
        viewModel.updateSearchQuery("Beta")
        assertEquals(listOf(customA, customB), viewModel.uiState.value.queueStations)
        assertEquals(listOf(customB), viewModel.uiState.value.visibleStations)

        viewModel.playStation(customB)
        advanceUntilIdle()
        assertEquals(listOf(customA, customB), playback.lastQueue)
        assertEquals(1, playback.lastStartIndex)
        assertFalse(viewModel.uiState.value.isSearchOpen)
    }

    @Test
    fun viewModelKeepsEditorOpenWithInlineErrorAndClosesAfterSuccessfulSave() = runTest(dispatcher) {
        val source = MutableCustomRadioDataSource()
        val failingValidator = RecordingValidator(failure = IllegalArgumentException("stream non raggiungibile"))
        val viewModel = RadioViewModel(
            RadioFeatureController(
                source,
                failingValidator,
                customStationIdFactory = { StationId("custom-fixed") },
            ),
            CustomTestPlaybackGateway(),
        )
        advanceUntilIdle()

        viewModel.openAddCustomStation()
        viewModel.updateCustomStationName("Test")
        viewModel.updateCustomStationUrl("https://example.com/live")
        viewModel.submitCustomStation()
        advanceUntilIdle()

        assertEquals("stream non raggiungibile", viewModel.uiState.value.customRadioEditor?.errorMessage)
        assertFalse(viewModel.uiState.value.customRadioEditor?.isSaving ?: true)
        assertTrue(source.stations().isEmpty())

        failingValidator.failure = null
        viewModel.submitCustomStation()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.customRadioEditor)
        assertEquals("Radio personale aggiunta", viewModel.uiState.value.customActionMessage)
        assertEquals(setOf(StationId("custom-fixed")), viewModel.uiState.value.customStationIds)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
        return try {
            block()
            throw AssertionError("Expected failure")
        } catch (error: Throwable) {
            if (error is AssertionError) throw error
            error
        }
    }

    private fun station(id: String, name: String, url: String) = RadioStation(
        id = StationId(id),
        name = name,
        primaryStream = StreamEndpoint(url),
    )

    private class RecordingValidator(
        var failure: Throwable? = null,
    ) : RadioStreamValidator {
        val urls = mutableListOf<String>()
        override suspend fun validate(url: String) {
            urls += url
            failure?.let { throw it }
        }
    }

    private class MutableCustomRadioDataSource(
        private val stations: MutableList<RadioStation> = mutableListOf(),
        private val favorites: MutableSet<StationId> = mutableSetOf(),
        private val customIds: MutableSet<StationId> = mutableSetOf(),
    ) : RadioDataSource {
        override suspend fun seedInitialCatalog() = Unit
        override suspend fun stations(): List<RadioStation> = stations.toList()
        override suspend fun favoriteIds(): Set<StationId> = favorites.toSet()
        override suspend fun customStationIds(): Set<StationId> = customIds.toSet()

        override suspend fun setFavorite(stationId: StationId, favorite: Boolean) {
            if (favorite) favorites += stationId else favorites -= stationId
        }

        override suspend fun saveCustomStation(station: RadioStation) {
            val index = stations.indexOfFirst { it.id == station.id }
            if (index >= 0) stations[index] = station else stations += station
            customIds += station.id
        }

        override suspend fun removeCustomStation(stationId: StationId): Boolean {
            if (stationId !in customIds) return false
            stations.removeAll { it.id == stationId }
            customIds -= stationId
            favorites -= stationId
            return true
        }
    }

    private class CustomTestPlaybackGateway : RadioPlaybackGateway {
        private val current = MutableStateFlow(PlaybackState(isConnected = true))
        override val playbackState: StateFlow<PlaybackState> = current.asStateFlow()
        var lastQueue: List<RadioStation> = emptyList()
        var lastStartIndex: Int = -1

        override fun play(
            stations: List<RadioStation>,
            startIndex: Int,
            onResult: (Result<Unit>) -> Unit,
        ) {
            lastQueue = stations.toList()
            lastStartIndex = startIndex
            val station = stations[startIndex]
            current.value = PlaybackState(
                isConnected = true,
                sourceType = MediaSourceType.RADIO,
                stationId = station.id,
                title = station.name,
                isPlaying = true,
                canSkipPrevious = stations.size > 1,
                canSkipNext = stations.size > 1,
            )
            onResult(Result.success(Unit))
        }
    }
}
