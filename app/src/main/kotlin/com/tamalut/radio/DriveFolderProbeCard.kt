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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.tamalut.radio.feature.drive.GoogleDriveApiClient
import com.tamalut.radio.feature.drive.GoogleDriveApiException
import com.tamalut.radio.feature.drive.GoogleDriveAuthorizationGateway
import com.tamalut.radio.feature.drive.GoogleDriveAuthorizationResultParser
import com.tamalut.radio.feature.drive.GoogleDriveFolderProbeReport
import com.tamalut.radio.feature.drive.GoogleDriveFolderProbeRunner
import com.tamalut.radio.feature.drive.redactDriveId
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface DriveProbeUiState {
    data object Idle : DriveProbeUiState
    data object Authorizing : DriveProbeUiState
    data object Reading : DriveProbeUiState
    data class Success(val report: GoogleDriveFolderProbeReport) : DriveProbeUiState
    data class Error(val message: String) : DriveProbeUiState
}

@Composable
internal fun DriveFolderProbeCard() {
    val context = LocalContext.current
    val authorizationGateway = remember(context) { GoogleDriveAuthorizationGateway.create(context) }
    val probeRunner = remember { GoogleDriveFolderProbeRunner(GoogleDriveApiClient()) }
    val overlayCoordinator = remember(context) { TamalutRadioRuntime.overlay(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<DriveProbeUiState>(DriveProbeUiState.Idle) }

    val consumeAuthorizationResult: (AuthorizationResult) -> Unit = { authorizationResult ->
        val grant = runCatching {
            GoogleDriveAuthorizationResultParser.requirePickerGrant(authorizationResult)
        }.getOrElse { error ->
            state = DriveProbeUiState.Error(error.safeDriveProbeMessage())
            null
        }

        if (grant != null) {
            state = DriveProbeUiState.Reading
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        probeRunner.probe(
                            accessToken = grant.accessToken,
                            selectedFolderId = grant.folderId,
                        )
                    }
                }
                state = result.fold(
                    onSuccess = { DriveProbeUiState.Success(it) },
                    onFailure = { DriveProbeUiState.Error(it.safeDriveProbeMessage()) },
                )
            }
        }
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) {
            state = DriveProbeUiState.Error("Selezione Google Drive annullata.")
            return@rememberLauncherForActivityResult
        }
        val data = activityResult.data
        if (data == null) {
            state = DriveProbeUiState.Error("Google Picker non ha restituito un risultato.")
            return@rememberLauncherForActivityResult
        }
        runCatching { authorizationGateway.resultFromIntent(data) }
            .onSuccess(consumeAuthorizationResult)
            .onFailure { state = DriveProbeUiState.Error(it.safeDriveProbeMessage()) }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Probe Google Drive · temporaneo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Verifica reale di drive.file: scegli la cartella test; TamalutRadio leggerà i figli diretti e poi la prima sottocartella trovata. Nessun token viene salvato e non viene avviata alcuna riproduzione.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                enabled = state !is DriveProbeUiState.Authorizing && state !is DriveProbeUiState.Reading,
                onClick = {
                    state = DriveProbeUiState.Authorizing
                    authorizationGateway.authorizeFolderSelection()
                        .addOnSuccessListener { authorizationResult ->
                            if (authorizationResult.hasResolution()) {
                                val pendingIntent = authorizationResult.pendingIntent
                                if (pendingIntent == null) {
                                    state = DriveProbeUiState.Error("Google Authorization non ha restituito il Picker.")
                                } else {
                                    overlayCoordinator.suppressNextAppStop()
                                    authorizationLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                                    )
                                }
                            } else {
                                consumeAuthorizationResult(authorizationResult)
                            }
                        }
                        .addOnFailureListener { error ->
                            state = DriveProbeUiState.Error(error.safeDriveProbeMessage())
                        }
                },
            ) {
                Text("Scegli cartella Drive e verifica")
            }

            DriveProbeResult(state)
        }
    }
}

@Composable
private fun DriveProbeResult(state: DriveProbeUiState) {
    when (state) {
        DriveProbeUiState.Idle -> Text(
            "Stato: non eseguito.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DriveProbeUiState.Authorizing -> Text("Stato: apertura account / Google Picker…")
        DriveProbeUiState.Reading -> Text("Stato: cartella scelta, interrogazione Drive API…")
        is DriveProbeUiState.Error -> {
            Text(
                "ESITO: ERRORE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }
        is DriveProbeUiState.Success -> DriveProbeSuccess(state.report)
    }
}

@Composable
private fun DriveProbeSuccess(report: GoogleDriveFolderProbeReport) {
    val nestedFolder = report.nestedFolder
    val nestedError = report.nestedError
    val nestedChildren = report.nestedChildren

    Text(
        "Cartella selezionata: ${redactDriveId(report.selectedFolderId)}",
        style = MaterialTheme.typography.labelLarge,
    )
    Text(
        "Figli diretti visibili: ${report.directChildren.size}",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (report.directChildren.isNotEmpty()) {
        Text(
            report.directChildren.take(8).joinToString(prefix = "Diretti: ", separator = " · ") { item ->
                if (item.isFolder) "📁 ${item.name}" else "♪ ${item.name}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            "La lista diretta è vuota. Se la cartella test contiene file, questo è il segnale critico da riportare: drive.file non sta esponendo i figli preesistenti.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }

    when {
        nestedFolder == null -> Text(
            "Sottocartella: non trovata tra i figli visibili. Per completare il gate usa una cartella test che contenga almeno una sottocartella.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        nestedError != null -> {
            Text(
                "Sottocartella '${nestedFolder.name}': ERRORE",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
            Text(nestedError, style = MaterialTheme.typography.bodySmall)
        }
        report.nestedAccessVerified -> {
            Text(
                "Sottocartella '${nestedFolder.name}': ACCESSO OK · ${nestedChildren.orEmpty().size} figli visibili",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            if (nestedChildren.orEmpty().isNotEmpty()) {
                Text(
                    nestedChildren.orEmpty().take(8).joinToString(
                        prefix = "Dentro: ",
                        separator = " · ",
                    ) { item -> if (item.isFolder) "📁 ${item.name}" else "♪ ${item.name}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Throwable.safeDriveProbeMessage(): String = when (this) {
    is GoogleDriveApiException -> message ?: "Google Drive API ha rifiutato la richiesta."
    is IOException -> "Errore di rete: verifica la connessione internet e riprova."
    else -> message?.takeIf { it.isNotBlank() } ?: "Operazione Google Drive non riuscita."
}
