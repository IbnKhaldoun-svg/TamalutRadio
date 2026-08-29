package com.tamalut.radio

import com.tamalut.radio.core.preferences.OverlayEdge

internal enum class OverlayToggleAction {
    DISABLE,
    ENABLE,
    ENABLE_AND_REQUEST_PERMISSION,
}

internal fun resolveOverlayToggleAction(
    requestedEnabled: Boolean,
    permissionGranted: Boolean,
): OverlayToggleAction = when {
    !requestedEnabled -> OverlayToggleAction.DISABLE
    permissionGranted -> OverlayToggleAction.ENABLE
    else -> OverlayToggleAction.ENABLE_AND_REQUEST_PERMISSION
}

internal fun shouldAdmitExternalOverlaySession(
    overlayEnabled: Boolean,
    permissionGranted: Boolean,
    isPlaying: Boolean,
): Boolean = overlayEnabled && permissionGranted && isPlaying

internal fun shouldShowOverlay(
    overlayEnabled: Boolean,
    permissionGranted: Boolean,
    appInForeground: Boolean,
    sessionState: OverlaySessionState,
    hasCurrentItem: Boolean,
): Boolean = overlayEnabled && permissionGranted && !appInForeground && sessionState.externalSessionActive && !sessionState.dismissedForCurrentSession && hasCurrentItem

internal object OverlayGeometry {
    fun clampY(y: Int, screenHeight: Int, windowHeight: Int): Int =
        y.coerceIn(0, (screenHeight - windowHeight).coerceAtLeast(0))

    fun normalizedVerticalFraction(y: Int, screenHeight: Int, windowHeight: Int): Float {
        val maxY = (screenHeight - windowHeight).coerceAtLeast(0)
        if (maxY == 0) return 0f
        return (clampY(y, screenHeight, windowHeight).toFloat() / maxY.toFloat()).coerceIn(0f, 1f)
    }

    fun yFromNormalizedFraction(fraction: Float, screenHeight: Int, windowHeight: Int): Int {
        val maxY = (screenHeight - windowHeight).coerceAtLeast(0)
        val normalized = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        return (maxY * normalized).toInt().coerceIn(0, maxY)
    }

    fun snapEdge(x: Int, screenWidth: Int, windowWidth: Int): OverlayEdge {
        val clampedX = x.coerceIn(0, (screenWidth - windowWidth).coerceAtLeast(0))
        val centerX = clampedX + (windowWidth / 2)
        return if (centerX < screenWidth / 2) OverlayEdge.LEFT else OverlayEdge.RIGHT
    }

    fun xForEdge(edge: OverlayEdge, screenWidth: Int, windowWidth: Int): Int = when (edge) {
        OverlayEdge.LEFT -> 0
        OverlayEdge.RIGHT -> (screenWidth - windowWidth).coerceAtLeast(0)
    }
}
