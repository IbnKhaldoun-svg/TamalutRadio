package com.tamalut.radio.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation-only bridge from the existing SleepTimerController state to system media UI.
 * It owns no clock, scheduler, deadline or expiry behavior.
 */
object SleepTimerNotificationBridge {
    private val mutableRemainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds: StateFlow<Long?> = mutableRemainingSeconds.asStateFlow()

    fun publish(state: SleepTimerState) {
        mutableRemainingSeconds.value = state.remainingSeconds
            .takeIf { state.isActive && it > 0L }
    }
}

fun formatSleepTimerRemaining(remainingSeconds: Long): String {
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

fun sleepTimerNotificationContentText(remainingSeconds: Long?): String? =
    remainingSeconds
        ?.takeIf { it > 0L }
        ?.let { seconds -> "Spegnimento tra ${formatSleepTimerRemaining(seconds)}" }
