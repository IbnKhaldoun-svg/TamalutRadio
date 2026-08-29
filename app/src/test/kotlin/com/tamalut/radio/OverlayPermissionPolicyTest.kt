package com.tamalut.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPermissionPolicyTest {
    @Test
    fun disablingAlwaysRemovesOverlayWithoutPermissionWork() {
        assertEquals(OverlayToggleAction.DISABLE, resolveOverlayToggleAction(false, false))
        assertEquals(OverlayToggleAction.DISABLE, resolveOverlayToggleAction(false, true))
    }

    @Test
    fun enablingWithPermissionShowsOverlay() {
        assertEquals(OverlayToggleAction.SHOW, resolveOverlayToggleAction(true, true))
    }

    @Test
    fun enablingWithoutPermissionRequiresExplicitPermissionFlow() {
        assertEquals(OverlayToggleAction.REQUEST_PERMISSION, resolveOverlayToggleAction(true, false))
    }
}
