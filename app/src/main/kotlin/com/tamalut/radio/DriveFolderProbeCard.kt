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
import com.tamalut.radio.feature.drive.GoogleDriveApiException
import com.tamalut.radio.feature.drive.GoogleDriveAuthorizationGateway
import com.tamalut.radio.feature.drive.GoogleDriveAuthorizationResultParser
import com.tamalut.radio.feature.drive.GoogleDriveDiagnosticMeasurement
import com.tamalut.radio.feature.drive.GoogleDriveDiagnosticVerdict
import com.tamalut.radio.feature.drive.GoogleDriveFolderProbeReport
import com.tamalut.radio.feature.drive.GoogleDriveFolderProbeRunner
import com.tamalut.radio.feature.drive.GoogleDriveProbeItem
import com.tamalut.radio.feature.drive.GoogleDriveSelectionException
import com.tamalut.radio.feature.drive.redactDriveId
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val DRIVE_PROBE_TIMEOUT_MILLIS = 120_000L
private const val DRIVE_DIAGNOSTIC_ITEMS_PER_PAGE_PREVIEW = 30

private sealed interface DriveProbeResultState {
    data object Idle : DriveProbeResultState
    data class Success(val report: GoogleDriveFolderProbeReport) : DriveProbeResultState
    data class Error(val message: String) : DriveProbeResultState
}

@Composable
internal fun DriveFolderProbeCard() {
    val context = LocalContext.current
    val probeRunner = remember { GoogleDriveFolderProbeRunner() }
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
                "Probe Google Drive A1/A2/B/C · temporaneo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Scope invariato: drive.file. Una sola selezione e un solo access token in memoria eseguono A1, A2, B e C in sequenza. Il probe usa esclusivamente GET (files.get/files.list), non salva token o cartella e non esegue alcuna scrittura.",
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
                        "Scegli cartella Drive ed esegui A1/A2/B/C"
                    } else {
                        "Scegli un'altra cartella e ripeti A1/A2/B/C"
                    },
                )
            }

            when (attemptState.phase) {
                DriveProbeAttemptPhase.AUTHORIZING -> Text("Stato: apertura account / Google Picker…")
                DriveProbeAttemptPhase.READING -> Text(
                    "Stato: stessa sessione/token · esecuzione A1 → A2 → B → C…",
                )
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
        is DriveProbeResultState.Success -> DriveProbeDiagnosticSuccess(state.report)
    }
}

@Composable
private fun DriveProbeDiagnosticSuccess(report: GoogleDriveFolderProbeReport) {
    Text(
        "Cartella: ${report.selectedFolder.name.ifBlank { "(nome non disponibile)" }}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        "ID: ${redactDriveId(report.selectedFolderId)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(
        diagnosticVerdictText(report),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = when (report.verdict) {
            GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_SINGLE_ITEM,
            GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_SELECTED_FOLDER_PLUS_SINGLE_CHILD ->
                MaterialTheme.colorScheme.secondary

            GoogleDriveDiagnosticVerdict.PARENT_QUERY_SPECIFIC_BROADER_UNIVERSE,
            GoogleDriveDiagnosticVerdict.SAME_TOKEN_PARENT_RESULTS_DIFFER,
            GoogleDriveDiagnosticVerdict.FORCED_PAGINATION_MISMATCH,
            GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_DIFFERS_FROM_PARENT_RESULT,
            GoogleDriveDiagnosticVerdict.INCOMPLETE_SEARCH,
            GoogleDriveDiagnosticVerdict.CONSISTENT_OTHER ->
                MaterialTheme.colorScheme.tertiary
        },
    )

    DiagnosticMeasurement(report.a1)
    DiagnosticMeasurement(report.a2)
    DiagnosticMeasurement(report.b)
    DiagnosticMeasurement(report.c)

    val cWithoutSelected = report.c.itemIds - report.selectedFolderId
    Text(
        "Confronto C: ${report.c.itemIds.size} ID visibili totali; ${cWithoutSelected.size} escludendo la cartella selezionata.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DiagnosticMeasurement(measurement: GoogleDriveDiagnosticMeasurement) {
    val queryDescription = if (measurement.parentFiltered) {
        "parent + trashed=false"
    } else {
        "solo trashed=false (nessun parent)"
    }

    Text(
        "${measurement.label} · $queryDescription · pageSize=${measurement.pageSize}",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )

    measurement.pages.forEach { page ->
        Text(
            "Pagina ${page.pageNumber}: ${page.items.size} elementi · next=${if (page.hasNextPage) "sì" else "no"} · incompleteSearch=${page.incompleteSearch}",
            style = MaterialTheme.typography.bodySmall,
        )

        if (page.items.isEmpty()) {
            Text(
                "— pagina vuota —",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                page.items
                    .take(DRIVE_DIAGNOSTIC_ITEMS_PER_PAGE_PREVIEW)
                    .joinToString(separator = "\n") { item -> diagnosticItemText(item) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (page.items.size > DRIVE_DIAGNOSTIC_ITEMS_PER_PAGE_PREVIEW) {
                Text(
                    "… altri ${page.items.size - DRIVE_DIAGNOSTIC_ITEMS_PER_PAGE_PREVIEW} elementi in questa pagina",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Text(
        "${measurement.label} totale: ${measurement.items.size} righe · ${measurement.itemIds.size} ID unici · pagine=${measurement.pages.size}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun diagnosticItemText(item: GoogleDriveProbeItem): String {
    val kind = if (item.isFolder) "📁" else "♪"
    val parents = if (item.parentIds.isEmpty()) {
        "parent=—"
    } else {
        "parent=" + item.parentIds.joinToString(",") { redactDriveId(it) }
    }
    return "• $kind ${item.name.ifBlank { "(senza nome)" }} · ${redactDriveId(item.id)} · $parents"
}

private fun diagnosticVerdictText(report: GoogleDriveFolderProbeReport): String = when (report.verdict) {
    GoogleDriveDiagnosticVerdict.INCOMPLETE_SEARCH ->
        "ESITO DIAGNOSTICO: INCONCLUSIVO — almeno una pagina ha incompleteSearch=true; non si può inferire l'universo completo."

    GoogleDriveDiagnosticVerdict.SAME_TOKEN_PARENT_RESULTS_DIFFER ->
        "ESITO DIAGNOSTICO: A1 e A2 differiscono pur usando lo stesso token e la stessa query. È un'anomalia da approfondire prima di attribuire il risultato allo scope."

    GoogleDriveDiagnosticVerdict.FORCED_PAGINATION_MISMATCH ->
        "ESITO DIAGNOSTICO: B (pageSize=1) produce un insieme diverso da A1/A2. La paginazione/query richiede ulteriore indagine."

    GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_SINGLE_ITEM ->
        "ESITO DIAGNOSTICO: C espone esattamente lo stesso unico ID visto dalla parent-query. Segnale forte che l'intero universo visibile della sessione drive.file è quel singolo elemento."

    GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_SELECTED_FOLDER_PLUS_SINGLE_CHILD ->
        "ESITO DIAGNOSTICO: C espone soltanto la cartella selezionata più lo stesso unico figlio visto da A1/A2/B. Escludendo la cartella, l'universo visibile coincide con un solo figlio: segnale forte di grant drive.file limitato."

    GoogleDriveDiagnosticVerdict.PARENT_QUERY_SPECIFIC_BROADER_UNIVERSE ->
        "ESITO DIAGNOSTICO: C espone un universo più ampio oltre alla cartella e al figlio isolato da A1/A2/B. Il problema è quindi specifico alla parent-query/visibilità dei figli e va approfondito."

    GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_DIFFERS_FROM_PARENT_RESULT ->
        "ESITO DIAGNOSTICO: C differisce dalla parent-query ma non come semplice superset. Risultato non conclusivo: conservare il dettaglio A1/A2/B/C per ulteriore analisi."

    GoogleDriveDiagnosticVerdict.CONSISTENT_OTHER ->
        "ESITO DIAGNOSTICO: A1/A2/B sono coerenti, ma la relazione con C non rientra nei casi conclusivi previsti. Conservare il report completo senza allargare lo scope."
}

private fun Throwable.safeDriveProbeMessage(): String = when (this) {
    is GoogleDriveSelectionException -> message ?: "Seleziona una cartella Google Drive e riprova."
    is GoogleDriveApiException -> message ?: "Google Drive API ha rifiutato la richiesta."
    is TimeoutCancellationException ->
        "Il diagnostico A1/A2/B/C ha superato il tempo massimo. Puoi riprovare subito."
    is IOException -> "Errore di rete: verifica la connessione internet e riprova."
    else -> message?.takeIf { it.isNotBlank() } ?: "Operazione Google Drive non riuscita."
}
