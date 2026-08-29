package com.tamalut.radio

internal enum class OverlayToggleAction {
    DISABLE,
    SHOW,
    REQUEST_PERMISSION,
}

internal fun resolveOverlayToggleAction(
    requestedEnabled: Boolean,
    permissionGranted: Boolean,
): OverlayToggleAction = when {
    !requestedEnabled -> OverlayToggleAction.DISABLE
    permissionGranted -> OverlayToggleAction.SHOW
    else -> OverlayToggleAction.REQUEST_PERMISSION
}
