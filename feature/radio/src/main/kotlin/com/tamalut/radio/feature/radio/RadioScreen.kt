package com.tamalut.radio.feature.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tamalut.radio.core.model.RadioStation

@Composable
fun RadioRoute(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    RadioScreen(
        state = state,
        onSectionSelected = viewModel::selectSection,
        onToggleFavorite = viewModel::toggleFavorite,
        onStationSelected = viewModel::playStation,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
fun RadioScreen(
    state: RadioUiState,
    onSectionSelected: (RadioSection) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onStationSelected: (RadioStation) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Radio",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Marocco · Italia · Sport",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryTabRow(
                selectedTabIndex = if (state.selectedSection == RadioSection.FAVORITES) 0 else 1,
            ) {
                Tab(
                    selected = state.selectedSection == RadioSection.FAVORITES,
                    onClick = { onSectionSelected(RadioSection.FAVORITES) },
                    text = { Text("Preferiti") },
                )
                Tab(
                    selected = state.selectedSection == RadioSection.ALL,
                    onClick = { onSectionSelected(RadioSection.ALL) },
                    text = { Text("Tutte le radio") },
                )
            }

            state.playbackMessage?.let {
                Text(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.playbackErrorMessage?.let {
                Text(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                state.isLoading -> LoadingState()
                state.errorMessage != null && state.stations.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                )
                state.selectedSection == RadioSection.FAVORITES && state.visibleStations.isEmpty() ->
                    EmptyFavoritesState()
                else -> RadioList(
                    stations = state.visibleStations,
                    favoriteIds = state.favoriteIds.mapTo(mutableSetOf()) { it.value },
                    playingStationId = state.playingStationId?.value,
                    onToggleFavorite = onToggleFavorite,
                    onStationSelected = onStationSelected,
                    transientError = state.errorMessage,
                )
            }
        }
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
            text = "Caricamento radio…",
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRetry) { Text("Riprova") }
    }
}

@Composable
private fun EmptyFavoritesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Nessuna radio preferita",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = "Tocca la stella accanto a una radio per aggiungerla qui.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RadioList(
    stations: List<RadioStation>,
    favoriteIds: Set<String>,
    playingStationId: String?,
    onToggleFavorite: (RadioStation) -> Unit,
    onStationSelected: (RadioStation) -> Unit,
    transientError: String?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (transientError != null) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    text = transientError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(stations, key = { it.id.value }) { station ->
            RadioStationRow(
                station = station,
                isFavorite = station.id.value in favoriteIds,
                isPlaying = station.id.value == playingStationId,
                onToggleFavorite = { onToggleFavorite(station) },
                onClick = { onStationSelected(station) },
            )
        }
    }
}

@Composable
private fun RadioStationRow(
    station: RadioStation,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    modifier = Modifier.padding(top = 3.dp),
                    text = when {
                        isPlaying -> "In riproduzione"
                        station.fallbackStreams.isEmpty() -> "Diretta radio"
                        else -> "Diretta · fallback disponibile"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                modifier = Modifier.semantics {
                    contentDescription = if (isFavorite) {
                        "Rimuovi ${station.name} dai preferiti"
                    } else {
                        "Aggiungi ${station.name} ai preferiti"
                    }
                },
                onClick = onToggleFavorite,
            ) {
                Text(
                    text = if (isFavorite) "★" else "☆",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
