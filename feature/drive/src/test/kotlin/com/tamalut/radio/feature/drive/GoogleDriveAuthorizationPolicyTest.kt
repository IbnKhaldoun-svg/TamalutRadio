package com.tamalut.radio.feature.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveAuthorizationPolicyTest {
    @Test
    fun `authorization policy is least privilege direct multi file and online only`() {
        assertEquals(
            setOf("https://www.googleapis.com/auth/drive.file"),
            GoogleDriveAuthorizationPolicy.requestedScopes,
        )
        assertTrue(GoogleDriveAuthorizationPolicy.pickerMimeTypes.isEmpty())
        assertFalse(GoogleDriveAuthorizationPolicy.includePreviouslyGrantedScopes)
        assertTrue(GoogleDriveAuthorizationPolicy.promptForConsent)
        assertTrue(GoogleDriveAuthorizationPolicy.promptForAccountSelection)
        assertTrue(GoogleDriveAuthorizationPolicy.pickerOAuthTrigger)
        assertFalse(GoogleDriveAuthorizationPolicy.allowFolderSelection)
        assertTrue(GoogleDriveAuthorizationPolicy.allowMultipleSelection)
        assertFalse(GoogleDriveAuthorizationPolicy.offlineAccess)
    }
}
