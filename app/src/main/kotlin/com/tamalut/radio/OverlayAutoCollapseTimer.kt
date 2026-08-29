package com.tamalut.radio

internal const val OVERLAY_AUTO_COLLAPSE_DELAY_MILLIS = 4_000L

internal fun interface OverlayScheduledTask {
    fun cancel()
}

internal class OverlayAutoCollapseTimer(
    private val schedule: (Long, () -> Unit) -> OverlayScheduledTask,
    private val onTimeout: () -> Unit,
    private val delayMillis: Long = OVERLAY_AUTO_COLLAPSE_DELAY_MILLIS,
) {
    private var generation = 0L
    private var scheduledTask: OverlayScheduledTask? = null

    fun arm() {
        generation += 1L
        val token = generation
        scheduledTask?.cancel()
        scheduledTask = schedule(delayMillis) {
            if (token == generation) {
                scheduledTask = null
                onTimeout()
            }
        }
    }

    fun cancel() {
        generation += 1L
        scheduledTask?.cancel()
        scheduledTask = null
    }
}
