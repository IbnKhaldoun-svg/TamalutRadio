package com.tamalut.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayActivityExitGateTest {
    @Test
    fun homeExitAdmitsExternalSessionWhenActivityStops() {
        val gate = OverlayActivityExitGate()
        assertEquals(OverlayActivityStopDecision.ADMIT_EXTERNAL_SESSION, gate.onAppStopped(false))
    }

    @Test
    fun recentsExitAdmitsExternalSessionWhenActivityStops() {
        val gate = OverlayActivityExitGate()
        assertEquals(OverlayActivityStopDecision.ADMIT_EXTERNAL_SESSION, gate.onAppStopped(false))
    }

    @Test
    fun rootBackExitAdmitsExternalSessionWhenActivityStops() {
        val gate = OverlayActivityExitGate()
        assertEquals(OverlayActivityStopDecision.ADMIT_EXTERNAL_SESSION, gate.onAppStopped(false))
    }

    @Test
    fun overlayPermissionSettingsSuppressesExactlyOneRealStop() {
        val gate = OverlayActivityExitGate()
        gate.suppressNextAppStop()
        assertEquals(OverlayActivityStopDecision.SUPPRESS_EXTERNAL_SESSION, gate.onAppStopped(false))
        assertEquals(OverlayActivityStopDecision.ADMIT_EXTERNAL_SESSION, gate.onAppStopped(false))
    }

    @Test
    fun configurationChangeDoesNotAdmitExternalSession() {
        val gate = OverlayActivityExitGate()
        assertEquals(OverlayActivityStopDecision.IGNORE_CONFIGURATION_CHANGE, gate.onAppStopped(true))
    }

    @Test
    fun foregroundReturnClearsUnusedSuppression() {
        val gate = OverlayActivityExitGate()
        gate.suppressNextAppStop()
        gate.onAppForeground()
        assertEquals(OverlayActivityStopDecision.ADMIT_EXTERNAL_SESSION, gate.onAppStopped(false))
    }
}
