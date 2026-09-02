package com.tamalut.radio.feature.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RadioSection {
    FAVORITES,
    ALL,
}

enum class CustomRadioEditorMode {
    ADD,
    EDIT,
}

data class CustomRadioEditorState(
    val mode: CustomRadioEditorMode,
    val stationId: StationId? = null,
    val name: String = "",
    val streamUrl: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

data class RadioUiState(
    val isLoading: Boolean = true,
    val selectedSection: RadioSection = RadioSection.ALL,
    val selectedFilter: RadioStationFilter = RadioStationFilter.ALL,
    val stations: List<RadioStation> = emptyList(),
    val favoriteIds: Set<StationId> = emptySet(),
    val customStationIds: Set<StationId> = emptySet(),
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    val errorMessage: String? = null,
    val playbackMessage: String? = null,
    val playbackErrorMessage: String? = null,
    val playingStationId: StationId? = null,
    val customRadioEditor: CustomRadioEditorState? = null,
    val pendingCustomDelete: RadioStation? = null,
    val isDeletingCustomStation: Boolean = false,
    val customActionMessage: String? = null,
) {
    val queueStations: List<RadioStation>
        get() = when (selectedSection) {
            RadioSection.FAVORITES -> stations.filter { it.id in favoriteIds }
            RadioSection.ALL -> RadioStationFiltering.apply(
                stations = stations,
                filter = selectedFilter,
                customStationIds = customStationIds,
            )
        }

    val visibleStations: List<RadioStation>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return queueStations
            return queueStations.filter { station ->
                station.name.contains(query, ignoreCase = true)
            }
        }
}

class RadioViewModel(
    private val controller: RadioFeatureController,
    private val playbackGateway: RadioPlaybackGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            playbackGateway.playbackState.collectLatest { playback ->
                if (!playback.isConnected) return@collectLatest
                _uiState.update { state ->
                    val stationId = playback.stationId.takeIf {
                        playback.sourceType == MediaSourceType.RADIO
                    }
                    val stationName = stationId
                        ?.let { id -> state.stations.firstOrNull { it.id == id }?.name }
                        ?: playback.title
                    val sharedPlaybackError = playback.playbackErrorMessage.takeIf {
                        playback.sourceType == MediaSourceType.RADIO
                    }
                    state.copy(
                        playingStationId = stationId,
                        playbackMessage = when {
                            sharedPlaybackError != null -> null
                            stationId == null -> null
                            playback.isPlaying -> "In riproduzione: ${stationName ?: stationId.value}"
                            else -> "Connessione a ${stationName ?: stationId.value}…"
                        },
                        playbackErrorMessage = sharedPlaybackError?.let { message ->
                            "Impossibile riprodurre ${stationName ?: stationId?.value ?: "la radio"}: $message"
                        },
                    )
                }
            }
        }
    }

    fun selectSection(section: RadioSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun selectFilter(filter: RadioStationFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun openSearch() {
        _uiState.update { it.copy(isSearchOpen = true, customActionMessage = null) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun closeSearch() {
        _uiState.update { it.copy(isSearchOpen = false, searchQuery = "") }
    }

    fun openAddCustomStation() {
        _uiState.update {
            it.copy(
                isSearchOpen = false,
                searchQuery = "",
                customRadioEditor = CustomRadioEditorState(mode = CustomRadioEditorMode.ADD),
                pendingCustomDelete = null,
                customActionMessage = null,
                errorMessage = null,
            )
        }
    }

    fun openEditCustomStation(station: RadioStation) {
        if (station.id !in _uiState.value.customStationIds) {
            _uiState.update { it.copy(errorMessage = "Le radio integrate non possono essere modificate") }
            return
        }
        _uiState.update {
            it.copy(
                isSearchOpen = false,
                searchQuery = "",
                customRadioEditor = CustomRadioEditorState(
                    mode = CustomRadioEditorMode.EDIT,
                    stationId = station.id,
                    name = station.name,
                    streamUrl = station.primaryStream.url,
                ),
                pendingCustomDelete = null,
                customActionMessage = null,
                errorMessage = null,
            )
        }
    }

    fun updateCustomStationName(name: String) {
        _uiState.update { state ->
            val editor = state.customRadioEditor ?: return@update state
            if (editor.isSaving) state else state.copy(customRadioEditor = editor.copy(name = name, errorMessage = null))
        }
    }

    fun updateCustomStationUrl(url: String) {
        _uiState.update { state ->
            val editor = state.customRadioEditor ?: return@update state
            if (editor.isSaving) state else state.copy(customRadioEditor = editor.copy(streamUrl = url, errorMessage = null))
        }
    }

    fun dismissCustomStationEditor() {
        _uiState.update { state ->
            if (state.customRadioEditor?.isSaving == true) state else state.copy(customRadioEditor = null)
        }
    }

    fun submitCustomStation() {
        val editor = _uiState.value.customRadioEditor ?: return
        if (editor.isSaving) return
        _uiState.update { state ->
            state.copy(customRadioEditor = state.customRadioEditor?.copy(isSaving = true, errorMessage = null))
        }

        viewModelScope.launch {
            runCatching {
                controller.saveCustomStation(
                    stationId = editor.stationId,
                    name = editor.name,
                    streamUrl = editor.streamUrl,
                )
            }.onSuccess { snapshot ->
                applySnapshot(snapshot)
                _uiState.update {
                    it.copy(
                        customRadioEditor = null,
                        customActionMessage = if (editor.mode == CustomRadioEditorMode.ADD) {
                            "Radio personale aggiunta"
                        } else {
                            "Radio personale aggiornata"
                        },
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        customRadioEditor = state.customRadioEditor?.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Impossibile salvare la radio personale",
                        ),
                    )
                }
            }
        }
    }

    fun requestDeleteCustomStation(station: RadioStation) {
        if (station.id !in _uiState.value.customStationIds) {
            _uiState.update { it.copy(errorMessage = "Le radio integrate non possono essere eliminate") }
            return
        }
        _uiState.update {
            it.copy(
                pendingCustomDelete = station,
                isDeletingCustomStation = false,
                customActionMessage = null,
                errorMessage = null,
            )
        }
    }

    fun dismissDeleteCustomStation() {
        _uiState.update { state ->
            if (state.isDeletingCustomStation) state else state.copy(pendingCustomDelete = null)
        }
    }

    fun confirmDeleteCustomStation() {
        val station = _uiState.value.pendingCustomDelete ?: return
        if (_uiState.value.isDeletingCustomStation) return
        _uiState.update { it.copy(isDeletingCustomStation = true) }

        viewModelScope.launch {
            runCatching { controller.deleteCustomStation(station.id) }
                .onSuccess { snapshot ->
                    applySnapshot(snapshot)
                    _uiState.update {
                        it.copy(
                            pendingCustomDelete = null,
                            isDeletingCustomStation = false,
                            customActionMessage = "Radio personale eliminata",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDeletingCustomStation = false,
                            errorMessage = error.message ?: "Impossibile eliminare la radio personale",
                        )
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { controller.load() }
                .onSuccess(::applySnapshot)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Impossibile caricare le radio",
                        )
                    }
                }
        }
    }

    fun toggleFavorite(station: RadioStation) {
        val previous = _uiState.value
        val wasFavorite = station.id in previous.favoriteIds
        val optimistic = if (wasFavorite) {
            previous.favoriteIds - station.id
        } else {
            previous.favoriteIds + station.id
        }
        _uiState.update { it.copy(favoriteIds = optimistic, errorMessage = null) }

        viewModelScope.launch {
            runCatching { controller.toggleFavorite(station.id, wasFavorite) }
                .onSuccess(::applySnapshot)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            favoriteIds = previous.favoriteIds,
                            errorMessage = error.message ?: "Impossibile aggiornare i preferiti",
                        )
                    }
                }
        }
    }

    fun playStation(station: RadioStation) {
        val queueSnapshot = _uiState.value.queueStations.toList()
        val startIndex = queueSnapshot.indexOfFirst { it.id == station.id }
        if (startIndex == -1) {
            _uiState.update {
                it.copy(
                    playbackMessage = null,
                    playbackErrorMessage = "Impossibile riprodurre ${station.name}: stazione non presente nella coda corrente",
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearchOpen = false,
                playbackMessage = "Connessione a ${station.name}…",
                playbackErrorMessage = null,
            )
        }
        playbackGateway.play(queueSnapshot, startIndex) { result ->
            result.onSuccess {
                _uiState.update { it.copy(playbackErrorMessage = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        playbackMessage = null,
                        playbackErrorMessage = "Impossibile riprodurre ${station.name}: ${error.message ?: "errore sconosciuto"}",
                    )
                }
            }
        }
    }

    private fun applySnapshot(snapshot: RadioSnapshot) {
        _uiState.update {
            it.copy(
                isLoading = false,
                stations = snapshot.stations,
                favoriteIds = snapshot.favoriteIds,
                customStationIds = snapshot.customStationIds,
                errorMessage = null,
            )
        }
    }

    override fun onCleared() {
        playbackGateway.release()
        super.onCleared()
    }
}

class RadioViewModelFactory(
    private val controller: RadioFeatureController,
    private val playbackGateway: RadioPlaybackGateway,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RadioViewModel::class.java))
        return RadioViewModel(controller, playbackGateway) as T
    }
}
