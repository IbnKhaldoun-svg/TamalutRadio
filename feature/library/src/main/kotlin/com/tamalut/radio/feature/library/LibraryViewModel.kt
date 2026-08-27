package com.tamalut.radio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tamalut.radio.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class LibraryUiState(
    val folderUri: String? = null,
    val isLoading: Boolean = true,
    val tracks: List<LocalAudioTrack> = emptyList(),
    val errorMessage: String? = null,
)

class LibraryViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val scanner: LocalAudioScanner,
    private val folderAccess: LocalFolderAccess,
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
                        _uiState.value = LibraryUiState(isLoading = false)
                    }
                    return@collectLatest
                }

                currentFolderUri = persistedFolderUri
                if (persistedFolderUri == null) {
                    _uiState.value = LibraryUiState(isLoading = false)
                } else {
                    scanFolder(persistedFolderUri)
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
            _uiState.value = _uiState.value.copy(
                folderUri = normalized,
                isLoading = true,
                errorMessage = null,
            )
            runCatching {
                folderAccess.persistReadPermission(normalized)
                currentFolderUri = normalized
                preferencesRepository.setLocalFolderUri(normalized)
                scanFolder(normalized)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    folderUri = currentFolderUri,
                    isLoading = false,
                    errorMessage = error.message ?: "Impossibile accedere alla cartella selezionata",
                )
            }
        }
    }

    fun refresh() {
        val folderUri = currentFolderUri ?: return
        viewModelScope.launch {
            scanFolder(folderUri)
        }
    }

    private suspend fun scanFolder(folderUri: String) {
        _uiState.value = _uiState.value.copy(
            folderUri = folderUri,
            isLoading = true,
            errorMessage = null,
        )
        runCatching { scanner.scan(folderUri) }
            .onSuccess { tracks ->
                _uiState.value = LibraryUiState(
                    folderUri = folderUri,
                    isLoading = false,
                    tracks = tracks,
                )
            }
            .onFailure { error ->
                _uiState.value = LibraryUiState(
                    folderUri = folderUri,
                    isLoading = false,
                    tracks = emptyList(),
                    errorMessage = error.message ?: "Impossibile leggere la cartella musicale",
                )
            }
    }
}

class LibraryViewModelFactory(
    private val preferencesRepository: UserPreferencesRepository,
    private val scanner: LocalAudioScanner,
    private val folderAccess: LocalFolderAccess,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
        return LibraryViewModel(
            preferencesRepository = preferencesRepository,
            scanner = scanner,
            folderAccess = folderAccess,
        ) as T
    }
}
