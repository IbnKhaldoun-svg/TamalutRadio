package com.tamalut.radio

internal enum class OverlayActivityStopDecision {
    IGNORE_CONFIGURATION_CHANGE,
    SUPPRESS_EXTERNAL_SESSION,
    ADMIT_EXTERNAL_SESSION,
}

internal class OverlayActivityExitGate {
    private var suppressNextStop = false

    fun onAppForeground() {
        suppressNextStop = false
    }

    fun suppressNextAppStop() {
        suppressNextStop = true
    }

    fun onAppStopped(isChangingConfigurations: Boolean): OverlayActivityStopDecision {
        if (isChangingConfigurations) {
            return OverlayActivityStopDecision.IGNORE_CONFIGURATION_CHANGE
        }
        if (suppressNextStop) {
            suppressNextStop = false
            return OverlayActivityStopDecision.SUPPRESS_EXTERNAL_SESSION
        }
        return OverlayActivityStopDecision.ADMIT_EXTERNAL_SESSION
    }
}
