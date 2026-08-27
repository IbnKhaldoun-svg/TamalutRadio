package com.tamalut.radio.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { viewModel.selectFolder(it.toString()) }
    }

    LibraryScreen(
        uiState = uiState,
        onChooseFolder = {
            folderPicker.launch(uiState.folderUri?.let(Uri::parse))
        },
        onRefresh = viewModel::refresh,
        onTrackClick = viewModel::playTrack,
        modifier = modifier,
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onChooseFolder: () -> Unit,
    onRefresh: () -> Unit,
    onTrackClick: (LocalAudioTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader()
            FolderPanel(
                folderUri = uiState.folderUri,
                isLoading = uiState.isLoading,
                onChooseFolder = onChooseFolder,
                onRefresh = onRefresh,
            )

            uiState.playbackMessage?.let { message ->
                StatusMessage(text = message, isError = false)
            }
            uiState.playbackErrorMessage?.let { message ->
                StatusMessage(text = message, isError = true)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    uiState.isLoading -> LoadingState()
                    uiState.errorMessage != null -> ErrorState(uiState.errorMessage)
                    uiState.folderUri == null -> NoFolderState()
                    uiState.tracks.isEmpty() -> EmptyLibraryState()
                    else -> TrackList(
                        tracks = uiState.tracks,
                        playingTrackId = uiState.playingTrackId,
                        onTrackClick = onTrackClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader() {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "LA TUA MUSICA",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Musica",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Locale · cartella selezionata da te",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderPanel(
    folderUri: String?,
    isLoading: Boolean,
    onChooseFolder: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = folderUri?.let(::friendlyFolderName) ?: "Scegli la cartella musicale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (folderUri == null) {
                            "TamalutRadio leggerà soltanto l'audio della cartella che autorizzi e delle sue sottocartelle."
                        } else {
                            "Accesso SAF persistente · nessun permesso storage generale"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onChooseFolder) {
                    Text(if (folderUri == null) "Scegli cartella" else "Cambia cartella")
                }
                if (folderUri != null) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                    ) {
                        Text("Aggiorna")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(text: String, isError: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.small,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        },
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = "Scansione della libreria…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    EmptyLikeState(
        title = "Impossibile leggere la cartella",
        message = message,
    )
}

@Composable
private fun NoFolderState() {
    EmptyLikeState(
        title = "Nessuna cartella selezionata",
        message = "Scegli una cartella musicale dal pannello qui sopra per creare la tua libreria locale.",
    )
}

@Composable
private fun EmptyLibraryState() {
    EmptyLikeState(
        title = "Nessun brano trovato",
        message = "La cartella selezionata non contiene ancora file audio riconosciuti.",
    )
}

@Composable
private fun EmptyLikeState(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackList(
    tracks: List<LocalAudioTrack>,
    playingTrackId: com.tamalut.radio.core.model.MediaId?,
    onTrackClick: (LocalAudioTrack) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = tracks,
            key = { track -> track.id.value },
        ) { track ->
            LocalTrackCard(
                track = track,
                isPlaying = playingTrackId == track.id,
                onClick = { onTrackClick(track) },
            )
        }
    }
}

@Composable
private fun LocalTrackCard(
    track: LocalAudioTrack,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 3.dp else 0.dp),
        border = BorderStroke(
            1.dp,
            if (isPlaying) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = if (isPlaying) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isPlaying) {
                    Text(
                        text = "In riproduzione",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                track.mimeType?.let { mimeType ->
                    Text(
                        text = mimeType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun friendlyFolderName(folderUri: String): String = folderUri
    .substringAfterLast('/')
    .takeIf(String::isNotBlank)
    ?: folderUri
