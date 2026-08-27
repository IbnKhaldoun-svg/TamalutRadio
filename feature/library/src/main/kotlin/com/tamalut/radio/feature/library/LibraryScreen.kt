package com.tamalut.radio.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Musica locale",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Scegli una cartella: TamalutRadio leggerà solo i file audio presenti lì e nelle sue sottocartelle.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onChooseFolder) {
            Text(if (uiState.folderUri == null) "Scegli cartella" else "Cambia cartella")
        }

        uiState.folderUri?.let { folderUri ->
            Text(
                text = "Cartella: ${folderUri.substringAfterLast('/').takeIf(String::isNotBlank) ?: folderUri}",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !uiState.isLoading,
            ) {
                Text("Aggiorna libreria")
            }
        }

        uiState.playbackMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        uiState.playbackErrorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.errorMessage != null -> Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
            uiState.folderUri == null -> Text("Nessuna cartella musicale selezionata.")
            uiState.tracks.isEmpty() -> Text("Nessun file audio trovato nella cartella selezionata.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = uiState.tracks,
                    key = { track -> track.id.value },
                ) { track ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (uiState.playingTrackId == track.id) {
                            Text(
                                text = "In riproduzione",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        track.mimeType?.let { mimeType ->
                            Text(
                                text = mimeType,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
