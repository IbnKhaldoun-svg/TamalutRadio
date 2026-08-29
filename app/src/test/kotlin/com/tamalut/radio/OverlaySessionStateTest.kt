package com.tamalut.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySessionStateTest {
    @Test
    fun dismissIsTemporaryInsideCurrentExternalSession() {
        val dismissed = OverlaySessionState()
            .beginExternalSession()
            .dismissForCurrentSession()

        assertTrue(dismissed.externalSessionActive)
        assertTrue(dismissed.dismissedForCurrentSession)
        assertFalse(dismissed.expanded)
    }

    @Test
    fun foregroundEndClearsDismissAndExpandedState() {
        val reset = OverlaySessionState()
            .beginExternalSession()
            .setExpanded(true)
            .dismissForCurrentSession()
            .endExternalSession()

        assertFalse(reset.externalSessionActive)
        assertFalse(reset.dismissedForCurrentSession)
        assertFalse(reset.expanded)
    }

    @Test
    fun laterExternalSessionStartsCollapsedAndNotDismissed() {
        val later = OverlaySessionState()
            .beginExternalSession()
            .dismissForCurrentSession()
            .endExternalSession()
            .beginExternalSession()

        assertTrue(later.externalSessionActive)
        assertFalse(later.dismissedForCurrentSession)
        assertFalse(later.expanded)
    }

    @Test
    fun repeatedAdmissionDoesNotResurrectDismissedOverlayInSameSession() {
        val dismissed = OverlaySessionState()
            .beginExternalSession()
            .dismissForCurrentSession()
            .beginExternalSession()

        assertTrue(dismissed.dismissedForCurrentSession)
    }
}
