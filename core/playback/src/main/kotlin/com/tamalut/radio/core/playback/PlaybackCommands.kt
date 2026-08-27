package com.tamalut.radio.core.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

object PlaybackCommands {
    const val STOP_EXIT_ACTION = "com.tamalut.radio.playback.STOP_EXIT"

    val stopExitCommand: SessionCommand
        get() = SessionCommand(STOP_EXIT_ACTION, Bundle.EMPTY)

    fun isStopExit(action: String): Boolean = action == STOP_EXIT_ACTION
}
