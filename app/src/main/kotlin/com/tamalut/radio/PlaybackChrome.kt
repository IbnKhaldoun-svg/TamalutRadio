package com.tamalut.radio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackModePolicy
import com.tamalut.radio.core.playback.PlaybackRepeatMode
import com.tamalut.radio.core.playback.PlaybackState

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

enum class PlaybackChromeAction {
    PREVIOUS,
    TOGGLE_PLAY_PAUSE,
    NEXT,
    TOGGLE_SHUFFLE,
    CYCLE_REPEAT,
}

fun PlaybackState.toPlaybackChromeModel(): PlaybackChromeModel? {
    if (!hasCurrentItem) return null
    val localActive = sourceType == MediaSourceType.LOCAL
    return PlaybackChromeModel(
        title = title.orEmpty().ifBlank { "In riproduzione" },
        sourceLabel = when (sourceType) {
            MediaSourceType.RADIO -> "Radio · LIVE"
            MediaSourceType.LOCAL -> "Musica locale"
            MediaSourceType.DRIVE -> "Google Drive"
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
                    .padding(horizontal = 8.dp),
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
    modifier: Modifier = Modifier,
) {
    val model = state.toPlaybackChromeModel()
    Surface(modifier = modifier) {
        if (model == null) {
            Column(
                modifier = Modifier.padding(24.dp),
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
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "IN RIPRODUZIONE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Surface(
                modifier = Modifier.size(184.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        modifier = Modifier.size(72.dp),
                        imageVector = if (state.sourceType == MediaSourceType.RADIO) Icons.Filled.Radio else Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text(
                text = model.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = model.sourceLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    text = if (model.isPlaying) "In riproduzione" else "In pausa",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
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
                        text = if (model.showLocalPlaybackModes) "Modalità musica locale" else "Dettagli radio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (model.showLocalPlaybackModes) {
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
                            text = "Streaming LIVE. Shuffle e loop sono disponibili soltanto per la musica locale.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "I controlli di trasporto restano sempre disponibili nel mini-player qui sotto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
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
