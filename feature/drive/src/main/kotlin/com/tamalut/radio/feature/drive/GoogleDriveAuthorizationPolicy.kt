package com.tamalut.radio.feature.drive

/**
 * Provider policy kept separate from Google SDK objects so least-privilege choices are
 * deterministic and easy to unit test.
 */
object GoogleDriveAuthorizationPolicy {
    const val PROVIDER_ID = "google-drive"
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

    val requestedScopes: Set<String> = setOf(DRIVE_FILE_SCOPE)
    val pickerMimeTypes: Set<String> = emptySet()
    const val includePreviouslyGrantedScopes = false
    const val promptForConsent = true
    const val promptForAccountSelection = true
    const val pickerOAuthTrigger = true
    const val allowFolderSelection = false
    const val allowMultipleSelection = true
    const val offlineAccess = false
}
