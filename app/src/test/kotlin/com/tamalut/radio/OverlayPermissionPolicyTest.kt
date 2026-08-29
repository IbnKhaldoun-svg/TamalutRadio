package com.tamalut.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPermissionPolicyTest {
    @Test
    fun disablingAlwaysChangesPermanentPreferenceOffWithoutPermissionWork() {
        assertEquals(OverlayToggleAction.DISABLE, resolveOverlayToggleAction(false, false))
        assertEquals(OverlayToggleAction.DISABLE, resolveOverlayToggleAction(false, true))
    }

    @Test
    fun enablingWithPermissionEnablesFeatureWithoutOpeningPermissionFlow() {
        assertEquals(OverlayToggleAction.ENABLE, resolveOverlayToggleAction(true, true))
    }

    @Test
    fun enablingWithoutPermissionKeepsEnableIntentAndRequestsExplicitPermissionFlow() {
        assertEquals(
            OverlayToggleAction.ENABLE_AND_REQUEST_PERMISSION,
            resolveOverlayToggleAction(true, false),
        )
    }

    @Test
    fun externalSessionAdmissionRequiresPreferencePermissionAndActivePlayback() {
        assertTrue(shouldAdmitExternalOverlaySession(true, true, true))
        assertFalse(shouldAdmitExternalOverlaySession(false, true, true))
        assertFalse(shouldAdmitExternalOverlaySession(true, false, true))
        assertFalse(shouldAdmitExternalOverlaySession(true, true, false))
    }

    @Test
    fun admittedOverlayRemainsVisibleWhenPlaybackIsPausedAsLongAsItemStillExists() {
        val admitted = OverlaySessionState().beginExternalSession()

        assertTrue(
            shouldShowOverlay(
                overlayEnabled = true,
                permissionGranted = true,
                appInForeground = false,
                sessionState = admitted,
                hasCurrentItem = true,
            ),
        )
    }

    @Test
    fun overlayHidesForForegroundDismissPermissionLossOrClearedItem() {
        val admitted = OverlaySessionState().beginExternalSession()
        val dismissed = admitted.dismissForCurrentSession()

        assertFalse(shouldShowOverlay(true, true, true, admitted, true))
        assertFalse(shouldShowOverlay(true, true, false, dismissed, true))
        assertFalse(shouldShowOverlay(true, false, false, admitted, true))
        assertFalse(shouldShowOverlay(true, true, false, admitted, false))
    }
}
