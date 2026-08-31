package com.tamalut.radio

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tamalut.radio.feature.drive.GoogleDriveAuthorizationGateway
import com.tamalut.radio.feature.drive.GoogleDriveAuthorizationResultParser
import com.tamalut.radio.feature.drive.redactDriveId

private sealed interface DriveMultiFilePickerState {
    data object Idle : DriveMultiFilePickerState
    data class Success(val pickedItemIds: List<String>) : DriveMultiFilePickerState
    data class Error(val message: String) : DriveMultiFilePickerState
}

@Composable
internal fun DriveMultiFilePickerCard() {
    val context = LocalContext.current
    val overlayCoordinator = remember(context) { TamalutRadioRuntime.overlay(context.applicationContext) }
    var state by remember { mutableStateOf<DriveMultiFilePickerState>(DriveMultiFilePickerState.Idle) }
    var busy by remember { mutableStateOf(false) }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) {
            busy = false
            state = DriveMultiFilePickerState.Error(
                "Selezione Google Drive annullata. Puoi riprovare subito.",
            )
            return@rememberLauncherForActivityResult
        }

        val data = activityResult.data
        if (data == null) {
            busy = false
            state = DriveMultiFilePickerState.Error(
                "Google Picker non ha restituito un risultato. Puoi riprovare subito.",
            )
            return@rememberLauncherForActivityResult
        }

        val gateway = GoogleDriveAuthorizationGateway.create(context)
        state = runCatching {
            val grant = GoogleDriveAuthorizationResultParser.requirePickerGrant(
                gateway.resultFromIntent(data),
            )
            DriveMultiFilePickerState.Success(grant.pickedItemIds)
        }.getOrElse { error ->
            DriveMultiFilePickerState.Error(
                error.message?.takeIf(String::isNotBlank)
                    ?: "Selezione Google Drive non riuscita.",
            )
        }
        busy = false
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Google Drive · multiselezione file",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Probe temporaneo Opzione A: scope drive.file, selezione diretta di più file nello stesso Picker. Nessuna cartella viene enumerata, nessun token o file selezionato viene persistito e non parte alcuna riproduzione.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Test A: entra in una cartella e seleziona almeno due MP3 prima di confermare. Test B: mentre il multiselect è attivo, controlla toolbar/menu del Picker e verifica se Google mostra nativamente “Seleziona tutto”. TamalutRadio non può abilitarlo via API.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                enabled = !busy,
                onClick = {
                    if (busy) return@TextButton
                    busy = true
                    state = DriveMultiFilePickerState.Idle
                    val gateway = GoogleDriveAuthorizationGateway.create(context)
                    gateway.authorizeFileSelection()
                        .addOnSuccessListener { authorizationResult ->
                            if (authorizationResult.hasResolution()) {
                                val pendingIntent = authorizationResult.pendingIntent
                                if (pendingIntent == null) {
                                    busy = false
                                    state = DriveMultiFilePickerState.Error(
                                        "Google Authorization non ha restituito il Picker. Puoi riprovare subito.",
                                    )
                                } else {
                                    overlayCoordinator.suppressNextAppStop()
                                    runCatching {
                                        authorizationLauncher.launch(
                                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                                        )
                                    }.onFailure { error ->
                                        busy = false
                                        state = DriveMultiFilePickerState.Error(
                                            error.message?.takeIf(String::isNotBlank)
                                                ?: "Impossibile aprire Google Picker.",
                                        )
                                    }
                                }
                            } else {
                                busy = false
                                state = DriveMultiFilePickerState.Error(
                                    "Google Authorization non ha aperto un nuovo Picker. Tocca di nuovo il pulsante per riprovare.",
                                )
                            }
                        }
                        .addOnFailureListener { error ->
                            busy = false
                            state = DriveMultiFilePickerState.Error(
                                error.message?.takeIf(String::isNotBlank)
                                    ?: "Autorizzazione Google Drive non riuscita.",
                            )
                        }
                },
            ) {
                Text(if (busy) "Apertura Google Picker…" else "Seleziona più file Drive")
            }

            when (val current = state) {
                DriveMultiFilePickerState.Idle -> Text(
                    if (busy) "Stato: apertura account / Google Picker…" else "Stato: non eseguito.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is DriveMultiFilePickerState.Error -> {
                    Text(
                        "ESITO: ERRORE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(current.message, style = MaterialTheme.typography.bodyMedium)
                }
                is DriveMultiFilePickerState.Success -> {
                    Text(
                        "ESITO: ${current.pickedItemIds.size} file selezionati",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    current.pickedItemIds.forEachIndexed { index, id ->
                        Text(
                            "${index + 1}. ${redactDriveId(id)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (current.pickedItemIds.size >= 2) {
                        Text(
                            "Test A: PASS · picked_file_ids contiene più elementi nella stessa sessione.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            "Test A: serve una nuova prova selezionando almeno due file prima di confermare.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
