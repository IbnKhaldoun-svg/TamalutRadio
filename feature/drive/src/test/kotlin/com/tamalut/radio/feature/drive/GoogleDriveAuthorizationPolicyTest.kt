package com.tamalut.radio.feature.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveAuthorizationPolicyTest {
    @Test
    fun `authorization policy is least privilege folder only and online only`() {
        assertEquals(
            setOf("https://www.googleapis.com/auth/drive.file"),
            GoogleDriveAuthorizationPolicy.requestedScopes,
        )
        assertEquals(
            setOf("application/vnd.google-apps.folder"),
            GoogleDriveAuthorizationPolicy.pickerMimeTypes,
        )
        assertFalse(GoogleDriveAuthorizationPolicy.includePreviouslyGrantedScopes)
        assertTrue(GoogleDriveAuthorizationPolicy.promptForConsent)
        assertTrue(GoogleDriveAuthorizationPolicy.promptForAccountSelection)
        assertTrue(GoogleDriveAuthorizationPolicy.pickerOAuthTrigger)
        assertTrue(GoogleDriveAuthorizationPolicy.allowFolderSelection)
        assertFalse(GoogleDriveAuthorizationPolicy.allowMultipleSelection)
        assertFalse(GoogleDriveAuthorizationPolicy.offlineAccess)
    }
}
