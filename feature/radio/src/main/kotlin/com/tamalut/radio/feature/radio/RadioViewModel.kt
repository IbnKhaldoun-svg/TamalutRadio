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

data class RadioUiState(
    val isLoading: Boolean = true,
    val selectedSection: RadioSection = RadioSection.ALL,
    val selectedFilter: RadioStationFilter = RadioStationFilter.ALL,
    val stations: List<RadioStation> = emptyList(),
    val favoriteIds: Set<StationId> = emptySet(),
    val errorMessage: String? = null,
    val playbackMessage: String? = null,
    val playbackErrorMessage: String? = null,
    val playingStationId: StationId? = null,
) {
    val visibleStations: List<RadioStation>
        get() = when (selectedSection) {
            RadioSection.FAVORITES -> stations.filter { it.id in favoriteIds }
            RadioSection.ALL -> RadioStationFiltering.apply(stations, selectedFilter)
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
        val queueSnapshot = _uiState.value.visibleStations.toList()
        val startIndex = queueSnapshot.indexOfFirst { it.id == station.id }
        if (startIndex == -1) {
            _uiState.update {
                it.copy(
                    playbackMessage = null,
                    playbackErrorMessage = "Impossibile riprodurre ${station.name}: stazione non presente nella lista corrente",
                )
            }
            return
        }

        _uiState.update {
            it.copy(
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
