package com.tamalut.radio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackModePolicy
import com.tamalut.radio.core.playback.PlaybackRepeatMode
import com.tamalut.radio.core.playback.PlaybackState
import com.tamalut.radio.core.playback.SleepTimerCustomDuration
import com.tamalut.radio.core.playback.SleepTimerPreset
import com.tamalut.radio.core.playback.SleepTimerState

data class PlaybackChromeModel(
    val title: String,
    val sourceLabel: String,
    val isPlaying: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val showLocalPlaybackModes: Boolean,
    val repeatMode: PlaybackRepeatMode,
    val shuffleEnabled: Boolean,
)

data class SleepTimerUiModel(
    val compactLabel: String,
    val detailLabel: String,
    val isActive: Boolean,
)

enum class PlaybackChromeAction {
    PREVIOUS,
    TOGGLE_PLAY_PAUSE,
    NEXT,
    TOGGLE_SHUFFLE,
    CYCLE_REPEAT,
}

internal val sleepTimerPresetOptions: List<SleepTimerPreset> = SleepTimerPreset.entries.toList()

fun SleepTimerState.toSleepTimerUiModel(): SleepTimerUiModel = if (isActive) {
    val remaining = formatSleepTimerRemaining(remainingSeconds)
    SleepTimerUiModel(
        compactLabel = "Timer $remaining",
        detailLabel = "$remaining rimanenti",
        isActive = true,
    )
} else {
    SleepTimerUiModel(
        compactLabel = "Timer",
        detailLabel = "Off",
        isActive = false,
    )
}

internal fun SleepTimerPreset.displayLabel(): String = when (this) {
    SleepTimerPreset.OFF -> "Off"
    SleepTimerPreset.MINUTES_15 -> "15 min"
    SleepTimerPreset.MINUTES_30 -> "30 min"
    SleepTimerPreset.MINUTES_45 -> "45 min"
    SleepTimerPreset.MINUTES_60 -> "60 min"
}

internal fun formatSleepTimerRemaining(remainingSeconds: Long): String {
    val safeSeconds = remainingSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    val seconds = safeSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

internal fun sleepTimerCustomDurationOrNull(hoursText: String, minutesText: String): SleepTimerCustomDuration? {
    val hours = hoursText.toIntOrNull() ?: return null
    val minutes = minutesText.toIntOrNull() ?: return null
    return SleepTimerCustomDuration.fromPartsOrNull(hours, minutes)
}

internal fun SleepTimerCustomDuration.toPreviewLabel(): String = when {
    hours > 0 && minutes > 0 -> "$hours h $minutes min"
    hours > 0 -> "$hours h"
    else -> "$minutes min"
}

fun PlaybackState.toPlaybackChromeModel(): PlaybackChromeModel? {
    if (!hasCurrentItem) return null
    val localActive = sourceType == MediaSourceType.LOCAL
    return PlaybackChromeModel(
        title = title.orEmpty().ifBlank { "In riproduzione" },
        sourceLabel = when (sourceType) {
            MediaSourceType.RADIO -> "Radio · LIVE"
            MediaSourceType.LOCAL -> "Musica locale"
            null -> ""
        },
        isPlaying = isPlaying,
        canSkipPrevious = canSkipPrevious,
        canSkipNext = canSkipNext,
        showLocalPlaybackModes = localActive,
        repeatMode = if (localActive) repeatMode else PlaybackRepeatMode.OFF,
        shuffleEnabled = localActive && shuffleEnabled,
    )
}

fun performPlaybackChromeAction(
    action: PlaybackChromeAction,
    state: PlaybackState,
    controller: PlaybackController,
) {
    when (action) {
        PlaybackChromeAction.PREVIOUS -> controller.skipToPrevious()
        PlaybackChromeAction.TOGGLE_PLAY_PAUSE -> controller.togglePlayPause()
        PlaybackChromeAction.NEXT -> controller.skipToNext()
        PlaybackChromeAction.TOGGLE_SHUFFLE -> {
            if (state.sourceType == MediaSourceType.LOCAL) {
                controller.setLocalShuffleEnabled(!state.shuffleEnabled)
            }
        }
        PlaybackChromeAction.CYCLE_REPEAT -> {
            if (state.sourceType == MediaSourceType.LOCAL) {
                controller.setLocalRepeatMode(PlaybackModePolicy.nextRepeatMode(state.repeatMode))
            }
        }
    }
}

@Composable
fun PersistentMiniPlayer(
    state: PlaybackState,
    controller: PlaybackController,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = state.toPlaybackChromeModel() ?: return

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        imageVector = if (state.sourceType == MediaSourceType.RADIO) Icons.Filled.Radio else Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clickable(onClick = onOpenNowPlaying),
            ) {
                Text(
                    text = model.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = model.sourceLabel,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (model.showLocalPlaybackModes) {
                IconButton(
                    onClick = {
                        performPlaybackChromeAction(PlaybackChromeAction.TOGGLE_SHUFFLE, state, controller)
                    },
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = if (state.shuffleEnabled) "Disattiva shuffle" else "Attiva shuffle",
                        tint = if (state.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = {
                        performPlaybackChromeAction(PlaybackChromeAction.CYCLE_REPEAT, state, controller)
                    },
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = if (state.repeatMode == PlaybackRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = repeatContentDescription(state.repeatMode),
                        tint = if (state.repeatMode == PlaybackRepeatMode.OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            IconButton(
                onClick = {
                    performPlaybackChromeAction(PlaybackChromeAction.PREVIOUS, state, controller)
                },
                enabled = model.canSkipPrevious,
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Precedente")
            }
            IconButton(
                onClick = {
                    performPlaybackChromeAction(PlaybackChromeAction.TOGGLE_PLAY_PAUSE, state, controller)
                },
            ) {
                Icon(
                    imageVector = if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (model.isPlaying) "Pausa" else "Riproduci",
                )
            }
            IconButton(
                onClick = {
                    performPlaybackChromeAction(PlaybackChromeAction.NEXT, state, controller)
                },
                enabled = model.canSkipNext,
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Successivo")
            }
        }
    }
}

@Composable
fun NowPlayingDestination(
    state: PlaybackState,
    sleepTimerState: SleepTimerState,
    onSleepTimerPresetSelected: (SleepTimerPreset) -> Unit,
    onCustomSleepTimerRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = state.toPlaybackChromeModel()
    val sleepTimerModel = sleepTimerState.toSleepTimerUiModel()
    Surface(modifier = modifier) {
        if (model == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "In Riproduzione",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Avvia una radio o un brano locale per vedere qui i dettagli della riproduzione.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "IN RIPRODUZIONE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Surface(
                modifier = Modifier.size(168.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        modifier = Modifier.size(68.dp),
                        imageVector = if (state.sourceType == MediaSourceType.RADIO) Icons.Filled.Radio else Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = model.sourceLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        text = if (model.isPlaying) "In riproduzione" else "In pausa",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Timer spegnimento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = sleepTimerModel.detailLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sleepTimerModel.isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sleepTimerPresetOptions.take(3).forEach { preset ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = !sleepTimerState.isCustom && sleepTimerState.preset == preset,
                                onClick = { onSleepTimerPresetSelected(preset) },
                                label = { Text(preset.displayLabel(), maxLines = 1) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sleepTimerPresetOptions.drop(3).forEach { preset ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = !sleepTimerState.isCustom && sleepTimerState.preset == preset,
                                onClick = { onSleepTimerPresetSelected(preset) },
                                label = { Text(preset.displayLabel(), maxLines = 1) },
                            )
                        }
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCustomSleepTimerRequested,
                    ) {
                        Text("Personalizzato…")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (model.showLocalPlaybackModes) {
                        Text(
                            text = "Riproduzione locale",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        PlaybackModeSummary(
                            icon = Icons.Filled.Shuffle,
                            label = if (model.shuffleEnabled) "Shuffle attivo" else "Shuffle disattivato",
                        )
                        PlaybackModeSummary(
                            icon = if (model.repeatMode == PlaybackRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            label = repeatModeLabel(model.repeatMode),
                        )
                    } else {
                        Text(
                            text = "Radio LIVE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Streaming in diretta. Shuffle e loop restano disponibili solo per la musica locale.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "I controlli di trasporto restano nel mini-player qui sotto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SleepTimerCustomDialog(
    initialDurationMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (SleepTimerCustomDuration) -> Unit,
) {
    val initial = remember(initialDurationMinutes) {
        SleepTimerCustomDuration.fromTotalMinutes(initialDurationMinutes ?: 30)
    }
    var hoursText by remember(initialDurationMinutes) { mutableStateOf(initial.hours.toString()) }
    var minutesText by remember(initialDurationMinutes) { mutableStateOf(initial.minutes.toString()) }
    val duration = sleepTimerCustomDurationOrNull(hoursText, minutesText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer personalizzato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = hoursText,
                        onValueChange = { value ->
                            if (value.length <= 2 && value.all(Char::isDigit)) hoursText = value
                        },
                        label = { Text("Ore") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = minutesText,
                        onValueChange = { value ->
                            if (value.length <= 2 && value.all(Char::isDigit)) minutesText = value
                        },
                        label = { Text("Minuti") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Text(
                    text = duration?.let { "Il timer scadrà tra ${it.toPreviewLabel()}" }
                        ?: "Inserisci una durata tra 1 minuto e 12 ore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = duration != null,
                onClick = { duration?.let(onConfirm) },
            ) { Text("Imposta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun PlaybackModeSummary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun repeatModeLabel(mode: PlaybackRepeatMode): String = when (mode) {
    PlaybackRepeatMode.OFF -> "Loop disattivato"
    PlaybackRepeatMode.ALL -> "Loop playlist"
    PlaybackRepeatMode.ONE -> "Loop brano"
}

private fun repeatContentDescription(mode: PlaybackRepeatMode): String = when (mode) {
    PlaybackRepeatMode.OFF -> "Attiva loop playlist"
    PlaybackRepeatMode.ALL -> "Attiva loop brano"
    PlaybackRepeatMode.ONE -> "Disattiva loop"
}
