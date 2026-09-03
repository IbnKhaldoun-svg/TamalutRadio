package com.tamalut.radio

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BackupRestoreSettings(
    coordinator: BackupRestoreCoordinator,
    contentResolver: ContentResolver,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<PreparedBackup?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isWorking = true
        statusMessage = null
        errorMessage = null
        scope.launch {
            runCatching {
                val bytes = coordinator.exportBytes()
                writeBackup(contentResolver, uri, bytes)
            }.onSuccess {
                statusMessage = "Backup esportato"
            }.onFailure { error ->
                errorMessage = error.message ?: "Impossibile esportare il backup"
            }
            isWorking = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isWorking = true
        statusMessage = null
        errorMessage = null
        scope.launch {
            runCatching {
                val bytes = readBackupBounded(contentResolver, uri)
                coordinator.prepareImport(bytes)
            }.onSuccess { prepared ->
                pendingRestore = prepared
            }.onFailure { error ->
                errorMessage = error.message ?: "Impossibile leggere il backup"
            }
            isWorking = false
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Backup e ripristino",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Salva radio personali, preferiti e impostazioni portabili in un file JSON scelto da te.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    enabled = !isWorking,
                    onClick = {
                        statusMessage = null
                        errorMessage = null
                        exportLauncher.launch(suggestedBackupFileName())
                    },
                ) {
                    Text("Esporta backup")
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    enabled = !isWorking,
                    onClick = {
                        statusMessage = null
                        errorMessage = null
                        importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                    },
                ) {
                    Text("Ripristina backup")
                }
            }
            if (isWorking) {
                Text(
                    "Operazione in corso…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            statusMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "Il backup è JSON leggibile e non cifrato: può contenere nomi, categorie e URL delle radio che hai creato. Conservalo dove ritieni opportuno.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingRestore?.let { prepared ->
        AlertDialog(
            onDismissRequest = {
                if (!isWorking) pendingRestore = null
            },
            title = { Text("Ripristinare questo backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Radio personali: ${prepared.preview.customStationCount}\n" +
                            "Preferiti: ${prepared.preview.favoriteCount}",
                    )
                    if (prepared.preview.skippedUnknownBuiltInFavoriteCount > 0) {
                        Text(
                            "${prepared.preview.skippedUnknownBuiltInFavoriteCount} preferiti di vecchie radio integrate non più presenti verranno ignorati.",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Text(
                        "I dati gestiti dal backup sostituiranno le radio personali e i preferiti attuali. La cartella Musica autorizzata e la riproduzione in corso non verranno modificate.",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isWorking,
                    onClick = {
                        isWorking = true
                        statusMessage = null
                        errorMessage = null
                        scope.launch {
                            runCatching { coordinator.restore(prepared) }
                                .onSuccess { result ->
                                    pendingRestore = null
                                    onRestored()
                                    statusMessage = buildString {
                                        append("Backup ripristinato: ")
                                        append(result.restoredCustomStationCount)
                                        append(" radio personali, ")
                                        append(result.restoredFavoriteCount)
                                        append(" preferiti")
                                        if (result.skippedUnknownBuiltInFavoriteCount > 0) {
                                            append("; ")
                                            append(result.skippedUnknownBuiltInFavoriteCount)
                                            append(" preferiti non più disponibili ignorati")
                                        }
                                        if (result.overlayPermissionRequired) {
                                            append(". Il Player flottante resta disattivato finché non concedi manualmente il permesso speciale")
                                        }
                                    }
                                }
                                .onFailure { error ->
                                    pendingRestore = null
                                    onRestored()
                                    errorMessage = error.message ?: "Impossibile ripristinare il backup"
                                }
                            isWorking = false
                        }
                    },
                ) {
                    Text("Ripristina")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isWorking,
                    onClick = { pendingRestore = null },
                ) {
                    Text("Annulla")
                }
            },
        )
    }
}

private suspend fun writeBackup(
    contentResolver: ContentResolver,
    uri: Uri,
    bytes: ByteArray,
) = withContext(Dispatchers.IO) {
    val stream = contentResolver.openOutputStream(uri, "wt")
        ?: throw IllegalArgumentException("Impossibile aprire il file di destinazione")
    stream.use { output ->
        output.write(bytes)
        output.flush()
    }
}

private suspend fun readBackupBounded(
    contentResolver: ContentResolver,
    uri: Uri,
): ByteArray = withContext(Dispatchers.IO) {
    val input = contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Impossibile aprire il file selezionato")
    input.use { stream ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_BACKUP_BYTES) { "Il backup supera il limite di 5 MiB" }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}
