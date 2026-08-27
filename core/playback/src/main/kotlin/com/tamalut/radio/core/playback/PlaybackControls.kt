package com.tamalut.radio.core.playback

import androidx.media3.common.Player
import androidx.media3.session.CommandButton

internal enum class PlaybackControlAction {
    PLAY_PAUSE,
    NEXT,
    STOP_EXIT,
}

internal data class PlaybackControlDefinition(
    val action: PlaybackControlAction,
    val displayName: String,
)

internal object PlaybackControls {
    val definitions: List<PlaybackControlDefinition> = listOf(
        PlaybackControlDefinition(PlaybackControlAction.PLAY_PAUSE, "Play/Pausa"),
        PlaybackControlDefinition(PlaybackControlAction.NEXT, "Successivo"),
        PlaybackControlDefinition(PlaybackControlAction.STOP_EXIT, "Stop/Esci"),
    )

    fun shouldExposeStopExit(
        isTrusted: Boolean,
        isMediaNotificationController: Boolean,
        isAutoCompanionController: Boolean,
        isAutomotiveController: Boolean,
    ): Boolean = isTrusted ||
        isMediaNotificationController ||
        isAutoCompanionController ||
        isAutomotiveController

    fun mediaButtonPreferences(includeStopExit: Boolean): List<CommandButton> = definitions
        .filter { includeStopExit || it.action != PlaybackControlAction.STOP_EXIT }
        .map(::toCommandButton)

    private fun toCommandButton(definition: PlaybackControlDefinition): CommandButton = when (definition.action) {
        PlaybackControlAction.PLAY_PAUSE -> CommandButton.Builder(CommandButton.ICON_PLAY)
            .setDisplayName(definition.displayName)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .build()

        PlaybackControlAction.NEXT -> CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName(definition.displayName)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

        PlaybackControlAction.STOP_EXIT -> CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName(definition.displayName)
            .setSessionCommand(PlaybackCommands.stopExitCommand)
            .build()
    }
}
