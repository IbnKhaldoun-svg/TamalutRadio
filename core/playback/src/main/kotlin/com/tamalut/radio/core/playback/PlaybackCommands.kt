package com.tamalut.radio.core.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand
import com.tamalut.radio.core.model.StationId

object PlaybackCommands {
    const val STOP_EXIT_ACTION = "com.tamalut.radio.playback.STOP_EXIT"
    const val RADIO_PLAYBACK_ERROR_ACTION = "com.tamalut.radio.playback.RADIO_PLAYBACK_ERROR"
    private const val RADIO_ERROR_STATION_ID = "com.tamalut.radio.playback.ERROR_STATION_ID"
    private const val RADIO_ERROR_CODE = "com.tamalut.radio.playback.ERROR_CODE"

    val stopExitCommand: SessionCommand
        get() = SessionCommand(STOP_EXIT_ACTION, Bundle.EMPTY)

    val radioPlaybackErrorCommand: SessionCommand
        get() = SessionCommand(RADIO_PLAYBACK_ERROR_ACTION, Bundle.EMPTY)

    fun isStopExit(action: String): Boolean = action == STOP_EXIT_ACTION

    fun isRadioPlaybackError(action: String): Boolean = action == RADIO_PLAYBACK_ERROR_ACTION

    fun radioPlaybackErrorArgs(stationId: StationId, errorCode: Int): Bundle = Bundle().apply {
        putString(RADIO_ERROR_STATION_ID, stationId.value)
        putInt(RADIO_ERROR_CODE, errorCode)
    }

    fun radioPlaybackErrorStationId(args: Bundle): StationId? =
        args.getString(RADIO_ERROR_STATION_ID)
            ?.let { rawId -> runCatching { StationId(rawId) }.getOrNull() }

    fun radioPlaybackErrorCode(args: Bundle): Int? =
        args.getInt(RADIO_ERROR_CODE, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
}
