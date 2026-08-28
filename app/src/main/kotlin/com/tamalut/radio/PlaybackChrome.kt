package com.tamalut.radio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamalut.radio.core.model.MediaSourceType
import com.tamalut.radio.core.playback.PlaybackController
import com.tamalut.radio.core.playback.PlaybackState

data class PlaybackChromeModel(
    val title: String,
    val sourceLabel: String,
    val isPlaying: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
)

enum class PlaybackChromeAction {
    PREVIOUS,
    TOGGLE_PLAY_PAUSE,
    NEXT,
}

fun PlaybackState.toPlaybackChromeModel(): PlaybackChromeModel? {
    if (!hasCurrentItem) return null
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
    )
}

fun performPlaybackChromeAction(
    action: PlaybackChromeAction,
    controller: PlaybackController,
) {
    when (action) {
        PlaybackChromeAction.PREVIOUS -> controller.skipToPrevious()
        PlaybackChromeAction.TOGGLE_PLAY_PAUSE -> controller.togglePlayPause()
        PlaybackChromeAction.NEXT -> controller.skipToNext()
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.sourceType == MediaSourceType.RADIO) Icons.Filled.Radio else Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
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
            IconButton(
                onClick = { performPlaybackChromeAction(PlaybackChromeAction.PREVIOUS, controller) },
                enabled = model.canSkipPrevious,
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Precedente")
            }
            IconButton(
                onClick = { performPlaybackChromeAction(PlaybackChromeAction.TOGGLE_PLAY_PAUSE, controller) },
            ) {
                Icon(
                    imageVector = if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (model.isPlaying) "Pausa" else "Riproduci",
                )
            }
            IconButton(
                onClick = { performPlaybackChromeAction(PlaybackChromeAction.NEXT, controller) },
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
    controller: PlaybackController,
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
                    text = "Avvia una radio o un brano locale per vedere qui i controlli del player.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Surface
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "IN RIPRODUZIONE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = model.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = model.sourceLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { performPlaybackChromeAction(PlaybackChromeAction.PREVIOUS, controller) },
                        enabled = model.canSkipPrevious,
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Precedente")
                    }
                    IconButton(
                        onClick = { performPlaybackChromeAction(PlaybackChromeAction.TOGGLE_PLAY_PAUSE, controller) },
                    ) {
                        Icon(
                            imageVector = if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (model.isPlaying) "Pausa" else "Riproduci",
                        )
                    }
                    IconButton(
                        onClick = { performPlaybackChromeAction(PlaybackChromeAction.NEXT, controller) },
                        enabled = model.canSkipNext,
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Successivo")
                    }
                }
            }
        }
    }
}
