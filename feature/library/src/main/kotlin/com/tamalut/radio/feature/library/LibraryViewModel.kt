package com.tamalut.radio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tamalut.radio.core.model.MediaId
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.playback.PlaybackModePolicy
import com.tamalut.radio.core.playback.PlaybackRepeatMode
import com.tamalut.radio.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val folderUri: String? = null,
    val isLoading: Boolean = true,
    val tracks: List<LocalAudioTrack> = emptyList(),
    val errorMessage: String? = null,
    val playbackMessage: String? = null,
    val playbackErrorMessage: String? = null,
    val playingTrackId: MediaId? = null,
    val isLocalPlaybackActive: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
)

class LibraryViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val scanner: LocalAudioScanner,
    private val folderAccess: LocalFolderAccess,
    private val playbackGateway: LocalPlaybackGateway = NoOpLocalPlaybackGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var currentFolderUri: String? = null

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collectLatest { preferences ->
                val persistedFolderUri = preferences.localFolderUri
                if (persistedFolderUri == currentFolderUri) {
                    if (persistedFolderUri == null && _uiState.value.isLoading) {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    return@collectLatest
                }

                currentFolderUri = persistedFolderUri
                if (persistedFolderUri == null) {
                    _uiState.update {
                        it.copy(
                            folderUri = null,
                            isLoading = false,
                            tracks = emptyList(),
                            errorMessage = null,
                            playingTrackId = null,
                        )
                    }
                } else {
                    scanFolder(persistedFolderUri)
                }
            }
        }
        viewModelScope.launch {
            playbackGateway.playbackState.collectLatest { playback ->
                if (!playback.isConnected) return@collectLatest
                _uiState.update { state ->
                    val localActive = playback.sourceType == MediaSourceType.LOCAL
                    val currentMediaId = playback.mediaId.takeIf { localActive }
                    state.copy(
                        playingTrackId = currentMediaId?.takeIf { mediaId ->
                            state.tracks.any { track -> track.id == mediaId }
                        },
                        isLocalPlaybackActive = localActive,
                        repeatMode = if (localActive) playback.repeatMode else PlaybackRepeatMode.OFF,
                        shuffleEnabled = localActive && playback.shuffleEnabled,
                        playbackMessage = if (localActive && playback.title != null) {
                            "In riproduzione: ${playback.title}"
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    fun selectFolder(treeUri: String) {
        val normalized = treeUri.trim()
        if (normalized.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    folderUri = normalized,
                    isLoading = true,
                    errorMessage = null,
                )
            }
            runCatching {
                folderAccess.persistReadPermission(normalized)
                currentFolderUri = normalized
                preferencesRepository.setLocalFolderUri(normalized)
                scanFolder(normalized)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        folderUri = currentFolderUri,
                        isLoading = false,
                        errorMessage = error.message ?: "Impossibile accedere alla cartella selezionata",
                    )
                }
            }
        }
    }

    fun refresh() {
        val folderUri = currentFolderUri ?: return
        viewModelScope.launch {
            scanFolder(folderUri)
        }
    }

    fun playTrack(track: LocalAudioTrack) {
        val tracks = _uiState.value.tracks
        if (tracks.none { it.id == track.id }) {
            return
        }
        _uiState.update {
            it.copy(
                playbackMessage = "Avvio di ${track.title}…",
                playbackErrorMessage = null,
            )
        }
        playbackGateway.play(tracks, track.id) { result ->
            result.onSuccess {
                _uiState.update {
                    it.copy(playbackErrorMessage = null)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        playbackMessage = null,
                        playbackErrorMessage = "Impossibile riprodurre ${track.title}: ${error.message ?: "errore sconosciuto"}",
                    )
                }
            }
        }
    }

    fun cycleRepeatMode() {
        if (!_uiState.value.isLocalPlaybackActive) return
        playbackGateway.setRepeatMode(
            PlaybackModePolicy.nextRepeatMode(_uiState.value.repeatMode),
        )
    }

    fun toggleShuffle() {
        if (!_uiState.value.isLocalPlaybackActive) return
        playbackGateway.setShuffleEnabled(!_uiState.value.shuffleEnabled)
    }

    private suspend fun scanFolder(folderUri: String) {
        _uiState.update {
            it.copy(
                folderUri = folderUri,
                isLoading = true,
                errorMessage = null,
            )
        }
        runCatching { scanner.scan(folderUri) }
            .onSuccess { tracks ->
                _uiState.update { current ->
                    current.copy(
                        folderUri = folderUri,
                        isLoading = false,
                        tracks = tracks,
                        errorMessage = null,
                        playingTrackId = current.playingTrackId?.takeIf { mediaId ->
                            tracks.any { track -> track.id == mediaId }
                        },
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        folderUri = folderUri,
                        isLoading = false,
                        tracks = emptyList(),
                        errorMessage = error.message ?: "Impossibile leggere la cartella musicale",
                        playingTrackId = null,
                    )
                }
            }
    }

    override fun onCleared() {
        playbackGateway.release()
        super.onCleared()
    }
}

class LibraryViewModelFactory(
    private val preferencesRepository: UserPreferencesRepository,
    private val scanner: LocalAudioScanner,
    private val folderAccess: LocalFolderAccess,
    private val playbackGateway: LocalPlaybackGateway,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
        return LibraryViewModel(
            preferencesRepository = preferencesRepository,
            scanner = scanner,
            folderAccess = folderAccess,
            playbackGateway = playbackGateway,
        ) as T
    }
}
