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
import com.tamalut.radio.feature.drive.GoogleDriveSelectionException
import com.tamalut.radio.feature.drive.redactDriveId
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val DRIVE_PROBE_TIMEOUT_MILLIS = 45_000L

private sealed interface DriveProbeResultState {
    data object Idle : DriveProbeResultState
    data class Success(val report: GoogleDriveFolderProbeReport) : DriveProbeResultState
    data class Error(val message: String) : DriveProbeResultState
}

@Composable
internal fun DriveFolderProbeCard() {
    val context = LocalContext.current
    val probeRunner = remember { GoogleDriveFolderProbeRunner(GoogleDriveApiClient()) }
    val overlayCoordinator = remember(context) { TamalutRadioRuntime.overlay(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var resultState by remember { mutableStateOf<DriveProbeResultState>(DriveProbeResultState.Idle) }
    var attemptState by remember { mutableStateOf(DriveProbeAttemptState()) }
    var pendingPickerAttemptId by remember { mutableStateOf<Long?>(null) }

    val finishWithError: (Long, String) -> Unit = { attemptId, message ->
        if (attemptState.activeAttemptId == attemptId) {
            resultState = DriveProbeResultState.Error(message)
            attemptState = attemptState.finish(attemptId)
        }
    }

    val consumeAuthorizationResult: (Long, AuthorizationResult) -> Unit = { attemptId, authorizationResult ->
        if (attemptState.activeAttemptId == attemptId) {
            val grant = runCatching {
                GoogleDriveAuthorizationResultParser.requirePickerGrant(authorizationResult)
            }.getOrElse { error ->
                finishWithError(attemptId, error.safeDriveProbeMessage())
                null
            }

            if (grant != null && attemptState.activeAttemptId == attemptId) {
                attemptState = attemptState.markReading(attemptId)
                scope.launch {
                    val result = runCatching {
                        withTimeout(DRIVE_PROBE_TIMEOUT_MILLIS) {
                            withContext(Dispatchers.IO) {
                                probeRunner.probe(
                                    accessToken = grant.accessToken,
                                    selectedItemId = grant.pickedItemId,
                                )
                            }
                        }
                    }
                    if (attemptState.activeAttemptId == attemptId) {
                        resultState = result.fold(
                            onSuccess = { DriveProbeResultState.Success(it) },
                            onFailure = { DriveProbeResultState.Error(it.safeDriveProbeMessage()) },
                        )
                        attemptState = attemptState.finish(attemptId)
                    }
                }
            }
        }
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        val attemptId = pendingPickerAttemptId
        pendingPickerAttemptId = null
        if (attemptId == null || attemptState.activeAttemptId != attemptId) {
            return@rememberLauncherForActivityResult
        }
        if (activityResult.resultCode != Activity.RESULT_OK) {
            finishWithError(attemptId, "Selezione Google Drive annullata. Puoi riprovare subito.")
            return@rememberLauncherForActivityResult
        }
        val data = activityResult.data
        if (data == null) {
            finishWithError(attemptId, "Google Picker non ha restituito un risultato. Puoi riprovare subito.")
            return@rememberLauncherForActivityResult
        }

        val resultGateway = GoogleDriveAuthorizationGateway.create(context)
        runCatching { resultGateway.resultFromIntent(data) }
            .onSuccess { consumeAuthorizationResult(attemptId, it) }
            .onFailure { finishWithError(attemptId, it.safeDriveProbeMessage()) }
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
                "Scope: drive.file. Il Picker è filtrato sulle sole cartelle; TamalutRadio verifica inoltre con files.get che l'elemento scelto sia davvero una cartella, poi usa soltanto files.list per i figli diretti e la prima sottocartella. Le chiamate Drive del probe sono esclusivamente GET: nessuna creazione, modifica o eliminazione.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                enabled = attemptState.canStart,
                onClick = {
                    if (!attemptState.canStart) return@TextButton

                    val started = attemptState.begin()
                    attemptState = started
                    resultState = DriveProbeResultState.Idle
                    val attemptId = requireNotNull(started.activeAttemptId)
                    val authorizationGateway = GoogleDriveAuthorizationGateway.create(context)

                    authorizationGateway.authorizeFolderSelection()
                        .addOnSuccessListener { authorizationResult ->
                            if (attemptState.activeAttemptId != attemptId) return@addOnSuccessListener

                            if (authorizationResult.hasResolution()) {
                                val pendingIntent = authorizationResult.pendingIntent
                                if (pendingIntent == null) {
                                    finishWithError(
                                        attemptId,
                                        "Google Authorization non ha restituito il Picker. Puoi riprovare subito.",
                                    )
                                } else {
                                    pendingPickerAttemptId = attemptId
                                    overlayCoordinator.suppressNextAppStop()
                                    runCatching {
                                        authorizationLauncher.launch(
                                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                                        )
                                    }.onFailure { error ->
                                        pendingPickerAttemptId = null
                                        finishWithError(attemptId, error.safeDriveProbeMessage())
                                    }
                                }
                            } else {
                                finishWithError(
                                    attemptId,
                                    "Google Authorization non ha aperto un nuovo Picker. Tocca di nuovo il pulsante per riprovare.",
                                )
                            }
                        }
                        .addOnFailureListener { error ->
                            finishWithError(attemptId, error.safeDriveProbeMessage())
                        }
                },
            ) {
                Text(
                    if (resultState is DriveProbeResultState.Idle) {
                        "Scegli cartella Drive e verifica"
                    } else {
                        "Scegli un'altra cartella Drive e verifica"
                    },
                )
            }

            when (attemptState.phase) {
                DriveProbeAttemptPhase.AUTHORIZING -> Text("Stato: apertura account / Google Picker…")
                DriveProbeAttemptPhase.READING -> Text("Stato: cartella verificata, interrogazione Drive API in sola lettura…")
                DriveProbeAttemptPhase.READY -> DriveProbeResult(resultState)
            }
        }
    }
}

@Composable
private fun DriveProbeResult(state: DriveProbeResultState) {
    when (state) {
        DriveProbeResultState.Idle -> Text(
            "Stato: non eseguito.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is DriveProbeResultState.Error -> {
            Text(
                "ESITO: ERRORE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }
        is DriveProbeResultState.Success -> DriveProbeSuccess(state.report)
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
    is GoogleDriveSelectionException -> message ?: "Seleziona una cartella Google Drive e riprova."
    is GoogleDriveApiException -> message ?: "Google Drive API ha rifiutato la richiesta."
    is TimeoutCancellationException -> "La lettura Google Drive ha superato il tempo massimo. Puoi riprovare subito."
    is IOException -> "Errore di rete: verifica la connessione internet e riprova."
    else -> message?.takeIf { it.isNotBlank() } ?: "Operazione Google Drive non riuscita."
}
