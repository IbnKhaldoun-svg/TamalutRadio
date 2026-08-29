package com.tamalut.radio.feature.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveAuthorizationPolicyTest {
    @Test
    fun `authorization policy is least privilege and online only`() {
        assertEquals(
            setOf("https://www.googleapis.com/auth/drive.file"),
            GoogleDriveAuthorizationPolicy.requestedScopes,
        )
        assertFalse(GoogleDriveAuthorizationPolicy.includePreviouslyGrantedScopes)
        assertTrue(GoogleDriveAuthorizationPolicy.promptForConsent)
        assertTrue(GoogleDriveAuthorizationPolicy.promptForAccountSelection)
        assertTrue(GoogleDriveAuthorizationPolicy.pickerOAuthTrigger)
        assertTrue(GoogleDriveAuthorizationPolicy.allowFolderSelection)
        assertFalse(GoogleDriveAuthorizationPolicy.offlineAccess)
    }
}
