package com.tamalut.radio

internal data class OverlaySessionState(
    val externalSessionActive: Boolean = false,
    val dismissedForCurrentSession: Boolean = false,
    val expanded: Boolean = false,
) {
    fun beginExternalSession(): OverlaySessionState = if (externalSessionActive) {
        this
    } else {
        copy(
            externalSessionActive = true,
            dismissedForCurrentSession = false,
            expanded = false,
        )
    }

    fun dismissForCurrentSession(): OverlaySessionState = if (!externalSessionActive) {
        this
    } else {
        copy(
            dismissedForCurrentSession = true,
            expanded = false,
        )
    }

    fun setExpanded(expanded: Boolean): OverlaySessionState = if (!externalSessionActive) {
        this
    } else {
        copy(expanded = expanded)
    }

    fun endExternalSession(): OverlaySessionState = OverlaySessionState()
}
