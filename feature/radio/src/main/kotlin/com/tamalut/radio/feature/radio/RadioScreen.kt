package com.tamalut.radio.feature.radio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
        onFilterSelected = viewModel::selectFilter,
        onToggleFavorite = viewModel::toggleFavorite,
        onStationSelected = viewModel::playStation,
        onSearchOpen = viewModel::openSearch,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSearchClear = viewModel::clearSearch,
        onSearchClose = viewModel::closeSearch,
        onAddCustomStation = viewModel::openAddCustomStation,
        onEditCustomStation = viewModel::openEditCustomStation,
        onDeleteCustomStation = viewModel::requestDeleteCustomStation,
        onCustomNameChange = viewModel::updateCustomStationName,
        onCustomUrlChange = viewModel::updateCustomStationUrl,
        onCustomSave = viewModel::submitCustomStation,
        onCustomEditorDismiss = viewModel::dismissCustomStationEditor,
        onCustomDeleteConfirm = viewModel::confirmDeleteCustomStation,
        onCustomDeleteDismiss = viewModel::dismissDeleteCustomStation,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
fun RadioScreen(
    state: RadioUiState,
    onSectionSelected: (RadioSection) -> Unit,
    onFilterSelected: (RadioStationFilter) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onStationSelected: (RadioStation) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchClose: () -> Unit,
    onRetry: () -> Unit,
    onAddCustomStation: () -> Unit = {},
    onEditCustomStation: (RadioStation) -> Unit = {},
    onDeleteCustomStation: (RadioStation) -> Unit = {},
    onCustomNameChange: (String) -> Unit = {},
    onCustomUrlChange: (String) -> Unit = {},
    onCustomSave: () -> Unit = {},
    onCustomEditorDismiss: () -> Unit = {},
    onCustomDeleteConfirm: () -> Unit = {},
    onCustomDeleteDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.isSearchOpen) {
        if (!state.isSearchOpen) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RadioHeader(
                isSearchOpen = state.isSearchOpen,
                onAddCustomStation = onAddCustomStation,
                onSearchOpen = onSearchOpen,
                onSearchClose = onSearchClose,
            )

            PrimaryTabRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                selectedTabIndex = if (state.selectedSection == RadioSection.FAVORITES) 0 else 1,
                containerColor = Color.Transparent,
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

            if (state.selectedSection == RadioSection.ALL) {
                RadioFilterSelector(
                    selectedFilter = state.selectedFilter,
                    onFilterSelected = onFilterSelected,
                )
            }

            if (state.isSearchOpen) {
                SearchTextField(
                    query = state.searchQuery,
                    placeholder = "Cerca radio",
                    onQueryChange = onSearchQueryChange,
                    onClear = onSearchClear,
                )
            }

            state.customActionMessage?.let { message ->
                StatusMessage(text = message, isError = false)
            }
            state.playbackMessage?.let { message ->
                StatusMessage(
                    text = message,
                    isError = false,
                )
            }
            state.playbackErrorMessage?.let { message ->
                StatusMessage(
                    text = message,
                    isError = true,
                )
            }

            when {
                state.isLoading -> LoadingState()
                state.errorMessage != null && state.stations.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                )
                state.selectedSection == RadioSection.FAVORITES && state.queueStations.isEmpty() ->
                    EmptyFavoritesState()
                state.selectedSection == RadioSection.ALL &&
                    state.selectedFilter == RadioStationFilter.PERSONAL &&
                    state.queueStations.isEmpty() -> EmptyCustomStationsState(onAddCustomStation)
                state.searchQuery.trim().isNotEmpty() && state.visibleStations.isEmpty() ->
                    EmptySearchState(
                        query = state.searchQuery.trim(),
                        onClear = onSearchClear,
                    )
                else -> RadioList(
                    stations = state.visibleStations,
                    favoriteIds = state.favoriteIds.mapTo(mutableSetOf()) { it.value },
                    customStationIds = state.customStationIds.mapTo(mutableSetOf()) { it.value },
                    playingStationId = state.playingStationId?.value,
                    autoScrollEnabled = state.searchQuery.isBlank(),
                    onToggleFavorite = onToggleFavorite,
                    onStationSelected = onStationSelected,
                    onEditCustomStation = onEditCustomStation,
                    onDeleteCustomStation = onDeleteCustomStation,
                    transientError = state.errorMessage,
                )
            }
        }
    }

    state.customRadioEditor?.let { editor ->
        CustomRadioEditorDialog(
            editor = editor,
            onNameChange = onCustomNameChange,
            onUrlChange = onCustomUrlChange,
            onSave = onCustomSave,
            onDismiss = onCustomEditorDismiss,
        )
    }

    state.pendingCustomDelete?.let { station ->
        DeleteCustomRadioDialog(
            station = station,
            isDeleting = state.isDeletingCustomStation,
            onConfirm = onCustomDeleteConfirm,
            onDismiss = onCustomDeleteDismiss,
        )
    }
}

@Composable
private fun RadioFilterSelector(
    selectedFilter: RadioStationFilter,
    onFilterSelected: (RadioStationFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioStationFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun RadioHeader(
    isSearchOpen: Boolean,
    onAddCustomStation: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "TAMALUT RADIO",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Radio",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onAddCustomStation) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Aggiungi radio personale",
                )
            }
            IconButton(onClick = if (isSearchOpen) onSearchClose else onSearchOpen) {
                Icon(
                    imageVector = if (isSearchOpen) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (isSearchOpen) "Chiudi ricerca radio" else "Cerca radio",
                )
            }
        }
        Text(
            text = "Marocco · Italia · Sport · UK · Personali",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchTextField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focusRequester),
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancella ricerca")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
    )
}

@Composable
private fun StatusMessage(text: String, isError: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            text = "Caricamento radio…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = Icons.Filled.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = "Nessuna radio preferita",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
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
private fun EmptyCustomStationsState(onAddCustomStation: () -> Unit) {
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
                imageVector = Icons.Filled.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = "Nessuna radio personale",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = "Aggiungi una radio inserendo il suo nome e una URL stream HTTPS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAddCustomStation) { Text("Aggiungi radio") }
    }
}

@Composable
private fun EmptySearchState(query: String, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            modifier = Modifier.size(40.dp),
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.padding(top = 14.dp),
            text = "Nessuna radio trovata per “$query”",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onClear) { Text("Cancella ricerca") }
    }
}

internal fun activeStationAutoScrollIndex(
    stations: List<RadioStation>,
    playingStationId: String?,
    autoScrollEnabled: Boolean,
): Int? {
    if (!autoScrollEnabled || playingStationId == null) return null
    return stations.indexOfFirst { it.id.value == playingStationId }.takeIf { it >= 0 }
}

@Composable
private fun RadioList(
    stations: List<RadioStation>,
    favoriteIds: Set<String>,
    customStationIds: Set<String>,
    playingStationId: String?,
    autoScrollEnabled: Boolean,
    onToggleFavorite: (RadioStation) -> Unit,
    onStationSelected: (RadioStation) -> Unit,
    onEditCustomStation: (RadioStation) -> Unit,
    onDeleteCustomStation: (RadioStation) -> Unit,
    transientError: String?,
) {
    val listState = rememberLazyListState()
    val stationIds = remember(stations) { stations.map { it.id.value } }

    LaunchedEffect(playingStationId, stationIds, autoScrollEnabled, transientError != null) {
        val stationIndex = activeStationAutoScrollIndex(
            stations = stations,
            playingStationId = playingStationId,
            autoScrollEnabled = autoScrollEnabled,
        ) ?: return@LaunchedEffect
        val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { item ->
            item.key == playingStationId
        }
        if (!alreadyVisible) {
            val leadingItemCount = if (transientError != null) 1 else 0
            listState.animateScrollToItem(stationIndex + leadingItemCount)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            RadioStationCard(
                station = station,
                isFavorite = station.id.value in favoriteIds,
                isCustom = station.id.value in customStationIds,
                isPlaying = station.id.value == playingStationId,
                onToggleFavorite = { onToggleFavorite(station) },
                onEdit = { onEditCustomStation(station) },
                onDelete = { onDeleteCustomStation(station) },
                onClick = { onStationSelected(station) },
            )
        }
    }
}

@Composable
private fun RadioStationCard(
    station: RadioStation,
    isFavorite: Boolean,
    isCustom: Boolean,
    isPlaying: Boolean,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val borderColor = if (isPlaying) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    }
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
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
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
                        imageVector = Icons.Filled.Radio,
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
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = station.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isPlaying) {
                        LiveBadge()
                    }
                }
                Text(
                    text = when {
                        isPlaying -> "In riproduzione"
                        isCustom -> "Radio personale"
                        station.fallbackStreams.isEmpty() -> "Diretta radio"
                        else -> "Diretta · fallback disponibile"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
                    color = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (isCustom) {
                CustomStationMenu(
                    stationName = station.name,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun CustomStationMenu(
    stationName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Gestisci $stationName",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Modifica") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Elimina") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun CustomRadioEditorDialog(
    editor: CustomRadioEditorState,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!editor.isSaving) onDismiss() },
        title = {
            Text(
                if (editor.mode == CustomRadioEditorMode.ADD) {
                    "Aggiungi radio personale"
                } else {
                    "Modifica radio personale"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = editor.name,
                    onValueChange = onNameChange,
                    enabled = !editor.isSaving,
                    singleLine = true,
                    label = { Text("Nome radio") },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = editor.streamUrl,
                    onValueChange = onUrlChange,
                    enabled = !editor.isSaving,
                    singleLine = true,
                    label = { Text("URL stream HTTPS") },
                )
                Text(
                    text = "Solo HTTPS. Prima del salvataggio verifichiamo connessione, redirect e sicurezza HLS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                editor.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !editor.isSaving,
                onClick = onSave,
            ) {
                if (editor.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Verifica e salva")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !editor.isSaving,
                onClick = onDismiss,
            ) {
                Text("Annulla")
            }
        },
    )
}

@Composable
private fun DeleteCustomRadioDialog(
    station: RadioStation,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("Eliminare ${station.name}?") },
        text = {
            Text(
                "La radio personale verrà rimossa dalla libreria e dai Preferiti. " +
                    "La riproduzione già attiva non viene forzatamente interrotta.",
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isDeleting,
                onClick = onConfirm,
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Elimina")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isDeleting,
                onClick = onDismiss,
            ) {
                Text("Annulla")
            }
        },
    )
}

@Composable
private fun LiveBadge() {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.tertiary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                modifier = Modifier.size(5.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.onTertiary,
            ) {}
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}
