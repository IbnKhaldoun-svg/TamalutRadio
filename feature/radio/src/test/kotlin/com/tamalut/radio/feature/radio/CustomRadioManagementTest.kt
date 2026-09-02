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
    fun addTrimsFieldsValidatesHttpsAndPersistsSelectedCategory() = runTest(dispatcher) {
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
            category = " italia ",
        )

        assertEquals(listOf("https://example.com/live"), validator.urls)
        assertEquals(setOf(StationId("custom-fixed")), result.customStationIds)
        assertEquals(mapOf(StationId("custom-fixed") to "Italia"), result.customStationCategories)
        assertEquals("Mia Radio", result.stations.single().name)
    }

    @Test
    fun editPreservesIdFavoriteAndCanMoveCategory() = runTest(dispatcher) {
        val custom = station("custom-one", "Old", "https://example.com/old")
        val source = MutableCustomRadioDataSource(
            stations = mutableListOf(custom),
            favorites = mutableSetOf(custom.id),
            customIds = mutableSetOf(custom.id),
            categories = mutableMapOf(custom.id to "Sport"),
        )
        val controller = RadioFeatureController(source, RecordingValidator())

        val result = controller.saveCustomStation(
            stationId = custom.id,
            name = "New Name",
            streamUrl = "https://example.com/new",
            category = "Jazz",
        )

        assertEquals(custom.id, result.stations.single().id)
        assertEquals("New Name", result.stations.single().name)
        assertTrue(custom.id in result.favoriteIds)
        assertEquals("Jazz", result.customStationCategories[custom.id])
    }

    @Test
    fun dynamicCategorySearchStillLaunchesCompletePreSearchCategoryQueue() = runTest(dispatcher) {
        val customA = station("custom-a", "Alpha Jazz", "https://example.com/a")
        val customB = station("custom-b", "Beta Jazz", "https://example.com/b")
        val source = MutableCustomRadioDataSource(
            stations = mutableListOf(customA, customB),
            customIds = mutableSetOf(customA.id, customB.id),
            categories = mutableMapOf(customA.id to "Jazz", customB.id to "Jazz"),
        )
        val playback = CustomTestPlaybackGateway()
        val viewModel = RadioViewModel(RadioFeatureController(source, RecordingValidator()), playback)
        advanceUntilIdle()

        val jazz = viewModel.uiState.value.availableFilters.single { it.label == "Jazz" }
        viewModel.selectFilter(jazz)
        viewModel.openSearch()
        viewModel.updateSearchQuery("Beta")
        assertEquals(listOf(customA, customB), viewModel.uiState.value.queueStations)
        assertEquals(listOf(customB), viewModel.uiState.value.visibleStations)

        viewModel.playStation(customB)
        advanceUntilIdle()
        assertEquals(listOf(customA, customB), playback.lastQueue)
        assertEquals(1, playback.lastStartIndex)
    }

    @Test
    fun settingsEditPickerIsTransientAndEditorPrefillsCategory() = runTest(dispatcher) {
        val custom = station("custom-one", "Custom", "https://example.com/custom")
        val source = MutableCustomRadioDataSource(
            stations = mutableListOf(custom),
            customIds = mutableSetOf(custom.id),
            categories = mutableMapOf(custom.id to "Sport"),
        )
        val viewModel = RadioViewModel(RadioFeatureController(source, RecordingValidator()), CustomTestPlaybackGateway())
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCustomEditPickerOpen)
        viewModel.openCustomStationEditPicker()
        assertTrue(viewModel.uiState.value.isCustomEditPickerOpen)
        assertEquals(listOf(custom), viewModel.uiState.value.customStations)

        viewModel.openEditCustomStation(custom)
        assertFalse(viewModel.uiState.value.isCustomEditPickerOpen)
        assertEquals("Sport", viewModel.uiState.value.customRadioEditor?.category)
        assertEquals(CustomRadioEditorMode.EDIT, viewModel.uiState.value.customRadioEditor?.mode)
    }

    @Test
    fun newReservedCategoryShowsInlineErrorWithoutNetworkOrSave() = runTest(dispatcher) {
        val source = MutableCustomRadioDataSource()
        val validator = RecordingValidator()
        val viewModel = RadioViewModel(
            RadioFeatureController(source, validator, customStationIdFactory = { StationId("custom-fixed") }),
            CustomTestPlaybackGateway(),
        )
        advanceUntilIdle()

        viewModel.openAddCustomStation()
        viewModel.updateCustomStationName("Test")
        viewModel.updateCustomStationUrl("https://example.com/live")
        viewModel.startNewCustomStationCategory()
        viewModel.updateNewCustomStationCategory("sport")
        viewModel.submitCustomStation()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.customRadioEditor?.errorMessage.orEmpty().contains("già disponibile"))
        assertTrue(validator.urls.isEmpty())
        assertTrue(source.stations().isEmpty())
    }

    @Test
    fun successfulSettingsAddCreatesDynamicFilterAndDeleteRemovesIt() = runTest(dispatcher) {
        val source = MutableCustomRadioDataSource()
        val viewModel = RadioViewModel(
            RadioFeatureController(
                source,
                RecordingValidator(),
                customStationIdFactory = { StationId("custom-fixed") },
            ),
            CustomTestPlaybackGateway(),
        )
        advanceUntilIdle()

        viewModel.openAddCustomStation()
        viewModel.updateCustomStationName("Amazigh Radio")
        viewModel.updateCustomStationUrl("https://example.com/live")
        viewModel.startNewCustomStationCategory()
        viewModel.updateNewCustomStationCategory("Amazigh")
        viewModel.submitCustomStation()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.customRadioEditor)
        assertEquals(listOf("Amazigh"), viewModel.uiState.value.userDefinedCategories)
        val saved = viewModel.uiState.value.customStations.single()
        viewModel.openEditCustomStation(saved)
        viewModel.requestDeleteCustomStation(saved)
        viewModel.confirmDeleteCustomStation()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.customStations.isEmpty())
        assertTrue(viewModel.uiState.value.userDefinedCategories.isEmpty())
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
        private val categories: MutableMap<StationId, String> = mutableMapOf(),
    ) : RadioDataSource {
        override suspend fun seedInitialCatalog() = Unit
        override suspend fun stations(): List<RadioStation> = stations.toList()
        override suspend fun favoriteIds(): Set<StationId> = favorites.toSet()
        override suspend fun customStationIds(): Set<StationId> = customIds.toSet()
        override suspend fun customStationCategories(): Map<StationId, String> = categories.toMap()

        override suspend fun setFavorite(stationId: StationId, favorite: Boolean) {
            if (favorite) favorites += stationId else favorites -= stationId
        }

        override suspend fun saveCustomStation(station: RadioStation, category: String) {
            val index = stations.indexOfFirst { it.id == station.id }
            if (index >= 0) stations[index] = station else stations += station
            customIds += station.id
            categories[station.id] = category
        }

        override suspend fun removeCustomStation(stationId: StationId): Boolean {
            if (stationId !in customIds) return false
            stations.removeAll { it.id == stationId }
            customIds -= stationId
            categories -= stationId
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
