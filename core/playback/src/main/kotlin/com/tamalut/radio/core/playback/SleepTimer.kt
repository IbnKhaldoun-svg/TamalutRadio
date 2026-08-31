package com.tamalut.radio.core.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SleepTimerPreset(val durationMinutes: Int?) {
    OFF(null),
    MINUTES_15(15),
    MINUTES_30(30),
    MINUTES_45(45),
    MINUTES_60(60),
}

data class SleepTimerCustomDuration private constructor(
    val totalMinutes: Int,
) {
    val hours: Int get() = totalMinutes / 60
    val minutes: Int get() = totalMinutes % 60

    companion object {
        const val MIN_TOTAL_MINUTES = 1
        const val MAX_TOTAL_MINUTES = 12 * 60

        fun fromTotalMinutes(totalMinutes: Int): SleepTimerCustomDuration {
            require(totalMinutes in MIN_TOTAL_MINUTES..MAX_TOTAL_MINUTES) {
                "Sleep timer duration must be between 1 minute and 12 hours"
            }
            return SleepTimerCustomDuration(totalMinutes)
        }

        fun fromPartsOrNull(hours: Int, minutes: Int): SleepTimerCustomDuration? {
            if (hours !in 0..12 || minutes !in 0..59) return null
            val totalMinutes = hours * 60 + minutes
            if (totalMinutes !in MIN_TOTAL_MINUTES..MAX_TOTAL_MINUTES) return null
            return SleepTimerCustomDuration(totalMinutes)
        }
    }
}

data class SleepTimerState(
    val preset: SleepTimerPreset = SleepTimerPreset.OFF,
    val customDurationMinutes: Int? = null,
    val remainingSeconds: Long = 0L,
) {
    val isCustom: Boolean get() = customDurationMinutes != null
    val isActive: Boolean
        get() = remainingSeconds > 0L && (preset != SleepTimerPreset.OFF || isCustom)
}

fun interface SleepTimerHandle { fun cancel() }

interface SleepTimerScheduler {
    fun nowMillis(): Long
    fun schedule(delayMillis: Long, action: () -> Unit): SleepTimerHandle
}

class HandlerSleepTimerScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime,
) : SleepTimerScheduler {
    override fun nowMillis(): Long = clockMillis()
    override fun schedule(delayMillis: Long, action: () -> Unit): SleepTimerHandle {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis.coerceAtLeast(0L))
        return SleepTimerHandle { handler.removeCallbacks(runnable) }
    }
}

class SleepTimerController(
    private val scheduler: SleepTimerScheduler,
    private val onExpired: () -> Unit,
) {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var deadlineMillis: Long? = null
    private var scheduledHandle: SleepTimerHandle? = null
    private var generation: Long = 0L

    fun setPreset(preset: SleepTimerPreset) {
        if (preset == SleepTimerPreset.OFF) {
            cancel()
            return
        }
        replaceTimer(
            durationMinutes = requireNotNull(preset.durationMinutes),
            preset = preset,
            customDurationMinutes = null,
        )
    }

    fun setCustomDuration(duration: SleepTimerCustomDuration) {
        replaceTimer(
            durationMinutes = duration.totalMinutes,
            preset = SleepTimerPreset.OFF,
            customDurationMinutes = duration.totalMinutes,
        )
    }

    private fun cancel() {
        generation += 1L
        scheduledHandle?.cancel()
        scheduledHandle = null
        deadlineMillis = null
        _state.value = SleepTimerState()
    }

    private fun replaceTimer(
        durationMinutes: Int,
        preset: SleepTimerPreset,
        customDurationMinutes: Int?,
    ) {
        generation += 1L
        scheduledHandle?.cancel()
        scheduledHandle = null
        val deadline = scheduler.nowMillis() + durationMinutes * MILLIS_PER_MINUTE
        deadlineMillis = deadline
        updateAndSchedule(preset, customDurationMinutes, deadline, generation)
    }

    private fun updateAndSchedule(
        preset: SleepTimerPreset,
        customDurationMinutes: Int?,
        deadline: Long,
        expectedGeneration: Long,
    ) {
        if (expectedGeneration != generation || deadlineMillis != deadline) return
        val remainingMillis = deadline - scheduler.nowMillis()
        if (remainingMillis <= 0L) {
            deadlineMillis = null
            scheduledHandle = null
            _state.value = SleepTimerState()
            onExpired()
            return
        }
        _state.value = SleepTimerState(
            preset = preset,
            customDurationMinutes = customDurationMinutes,
            remainingSeconds = (remainingMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND,
        )
        scheduledHandle = scheduler.schedule(minOf(MILLIS_PER_SECOND, remainingMillis)) {
            updateAndSchedule(preset, customDurationMinutes, deadline, expectedGeneration)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
