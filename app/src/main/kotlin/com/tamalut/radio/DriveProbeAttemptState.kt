package com.tamalut.radio

internal enum class DriveProbeAttemptPhase {
    READY,
    AUTHORIZING,
    READING,
}

internal data class DriveProbeAttemptState(
    val generation: Long = 0L,
    val activeAttemptId: Long? = null,
    val phase: DriveProbeAttemptPhase = DriveProbeAttemptPhase.READY,
) {
    val canStart: Boolean get() = activeAttemptId == null

    fun begin(): DriveProbeAttemptState {
        check(canStart) { "A Drive probe attempt is already active" }
        val nextId = generation + 1L
        return copy(
            generation = nextId,
            activeAttemptId = nextId,
            phase = DriveProbeAttemptPhase.AUTHORIZING,
        )
    }

    fun markReading(attemptId: Long): DriveProbeAttemptState =
        if (activeAttemptId == attemptId) copy(phase = DriveProbeAttemptPhase.READING) else this

    fun finish(attemptId: Long): DriveProbeAttemptState =
        if (activeAttemptId == attemptId) {
            copy(activeAttemptId = null, phase = DriveProbeAttemptPhase.READY)
        } else {
            this
        }
}
