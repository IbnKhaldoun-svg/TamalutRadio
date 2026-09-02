package com.tamalut.radio.feature.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tamalut.radio.core.model.RadioStation

@Composable
fun RadioManagementSettings(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Gestione radio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Aggiungi una radio o modifica quelle create da te. Le radio vengono mostrate nella categoria scelta nella schermata Radio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::openAddCustomStation,
                ) {
                    Text("Aggiungi radio")
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::openCustomStationEditPicker,
                ) {
                    Text("Modifica radio")
                }
            }
            state.customActionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (state.isCustomEditPickerOpen) {
        CustomRadioPickerDialog(
            stations = state.customStations,
            onSelect = viewModel::openEditCustomStation,
            onDismiss = viewModel::dismissCustomStationEditPicker,
        )
    }

    state.customRadioEditor?.let { editor ->
        val editingStation = editor.stationId?.let { stationId ->
            state.stations.firstOrNull { it.id == stationId }
        }
        CustomRadioSettingsEditorDialog(
            editor = editor,
            existingCustomCategories = state.userDefinedCategories,
            editingStation = editingStation,
            onNameChange = viewModel::updateCustomStationName,
            onUrlChange = viewModel::updateCustomStationUrl,
            onCategorySelected = viewModel::selectCustomStationCategory,
            onNewCategoryRequested = viewModel::startNewCustomStationCategory,
            onNewCategoryNameChange = viewModel::updateNewCustomStationCategory,
            onDelete = viewModel::requestDeleteCustomStation,
            onSave = viewModel::submitCustomStation,
            onDismiss = viewModel::dismissCustomStationEditor,
        )
    }

    state.pendingCustomDelete?.let { station ->
        DeleteCustomRadioSettingsDialog(
            station = station,
            isDeleting = state.isDeletingCustomStation,
            onConfirm = viewModel::confirmDeleteCustomStation,
            onDismiss = viewModel::dismissDeleteCustomStation,
        )
    }
}

@Composable
private fun CustomRadioPickerDialog(
    stations: List<RadioStation>,
    onSelect: (RadioStation) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica radio") },
        text = {
            if (stations.isEmpty()) {
                Text("Non hai ancora aggiunto radio da modificare.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(stations, key = { it.id.value }) { station ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(station) },
                        ) {
                            Text(station.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
    )
}

@Composable
private fun CustomRadioSettingsEditorDialog(
    editor: CustomRadioEditorState,
    existingCustomCategories: List<String>,
    editingStation: RadioStation?,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onNewCategoryRequested: () -> Unit,
    onNewCategoryNameChange: (String) -> Unit,
    onDelete: (RadioStation) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val categoryOptions = RadioCategoryRules.standardCategories + existingCustomCategories
    AlertDialog(
        onDismissRequest = { if (!editor.isSaving) onDismiss() },
        title = {
            Text(
                if (editor.mode == CustomRadioEditorMode.ADD) {
                    "Aggiungi radio"
                } else {
                    "Modifica radio"
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
                CategorySelector(
                    selectedCategory = editor.category,
                    options = categoryOptions,
                    isCreatingCategory = editor.isCreatingCategory,
                    enabled = !editor.isSaving,
                    onCategorySelected = onCategorySelected,
                    onNewCategoryRequested = onNewCategoryRequested,
                )
                if (editor.isCreatingCategory) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = editor.newCategoryName,
                        onValueChange = onNewCategoryNameChange,
                        enabled = !editor.isSaving,
                        singleLine = true,
                        label = { Text("Nome nuova categoria") },
                    )
                }
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
                if (editor.mode == CustomRadioEditorMode.EDIT && editingStation != null) {
                    TextButton(
                        enabled = !editor.isSaving,
                        onClick = { onDelete(editingStation) },
                    ) {
                        Text("Elimina radio", color = MaterialTheme.colorScheme.error)
                    }
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
private fun CategorySelector(
    selectedCategory: String,
    options: List<String>,
    isCreatingCategory: Boolean,
    enabled: Boolean,
    onCategorySelected: (String) -> Unit,
    onNewCategoryRequested: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Categoria",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onClick = { expanded = true },
            ) {
                Text(
                    when {
                        isCreatingCategory -> "+ Nuova categoria…"
                        selectedCategory.isNotBlank() -> selectedCategory
                        else -> "Seleziona categoria"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.distinctBy { it.lowercase() }.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            expanded = false
                            onCategorySelected(category)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("+ Nuova categoria…") },
                    onClick = {
                        expanded = false
                        onNewCategoryRequested()
                    },
                )
            }
        }
    }
}

@Composable
private fun DeleteCustomRadioSettingsDialog(
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
                "La radio verrà rimossa dalla libreria e dai Preferiti. " +
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
