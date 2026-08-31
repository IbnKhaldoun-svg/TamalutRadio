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

data class SleepTimerState(
    val preset: SleepTimerPreset = SleepTimerPreset.OFF,
    val remainingSeconds: Long = 0L,
) {
    val isActive: Boolean
        get() = preset != SleepTimerPreset.OFF && remainingSeconds > 0L
}

fun interface SleepTimerHandle {
    fun cancel()
}

interface SleepTimerScheduler {
    fun nowMillis(): Long

    fun schedule(
        delayMillis: Long,
        action: () -> Unit,
    ): SleepTimerHandle
}

class HandlerSleepTimerScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime,
) : SleepTimerScheduler {
    override fun nowMillis(): Long = clockMillis()

    override fun schedule(
        delayMillis: Long,
        action: () -> Unit,
    ): SleepTimerHandle {
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
        generation += 1L
        scheduledHandle?.cancel()
        scheduledHandle = null

        if (preset == SleepTimerPreset.OFF) {
            deadlineMillis = null
            _state.value = SleepTimerState()
            return
        }

        val durationMinutes = requireNotNull(preset.durationMinutes)
        val deadline = scheduler.nowMillis() + durationMinutes * MILLIS_PER_MINUTE
        deadlineMillis = deadline
        updateAndSchedule(
            preset = preset,
            deadline = deadline,
            expectedGeneration = generation,
        )
    }

    private fun updateAndSchedule(
        preset: SleepTimerPreset,
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
            remainingSeconds = (remainingMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND,
        )
        scheduledHandle = scheduler.schedule(minOf(MILLIS_PER_SECOND, remainingMillis)) {
            updateAndSchedule(
                preset = preset,
                deadline = deadline,
                expectedGeneration = expectedGeneration,
            )
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
