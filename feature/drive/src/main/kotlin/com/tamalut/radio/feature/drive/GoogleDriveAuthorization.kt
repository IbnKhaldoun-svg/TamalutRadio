package com.tamalut.radio.feature.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task

object GoogleDriveAuthorizationRequestFactory {
    fun folderPickerRequest(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(GoogleDriveAuthorizationPolicy.DRIVE_FILE_SCOPE)))
        .setOptOutIncludingGrantedScopes(true)
        .setPrompt(
            AuthorizationRequest.Prompt.CONSENT or
                AuthorizationRequest.Prompt.SELECT_ACCOUNT,
        )
        .addResourceParameter(
            AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER,
            "true",
        )
        .addResourceParameter(
            AuthorizationRequest.ResourceParameter.PICKER_ALLOW_FOLDER_SELECTION,
            "true",
        )
        .addResourceParameter(
            AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES,
            GoogleDriveAuthorizationPolicy.DRIVE_FOLDER_MIME_TYPE,
        )
        .build()
}

class GoogleDrivePickerGrant internal constructor(
    val pickedItemId: String,
    val accessToken: String,
) {
    override fun toString(): String =
        "GoogleDrivePickerGrant(pickedItemId=${redactDriveId(pickedItemId)}, accessToken=<redacted>)"
}

object GoogleDriveAuthorizationResultParser {
    const val PICKED_FILE_IDS_PARAM = "picked_file_ids"

    internal fun parsePickedItemId(rawPickedIds: String?): String? = rawPickedIds
        ?.split(',')
        ?.asSequence()
        ?.map(String::trim)
        ?.firstOrNull(String::isNotBlank)

    fun requirePickerGrant(result: AuthorizationResult): GoogleDrivePickerGrant {
        check(GoogleDriveAuthorizationPolicy.DRIVE_FILE_SCOPE in result.grantedScopes) {
            "Google Drive drive.file scope was not granted"
        }
        val accessToken = result.accessToken?.takeIf(String::isNotBlank)
            ?: error("Google Drive authorization returned no access token")
        val pickedItemId = parsePickedItemId(
            result.tokenResponseParams?.getString(PICKED_FILE_IDS_PARAM),
        ) ?: error("Google Picker returned no selected item ID")

        return GoogleDrivePickerGrant(
            pickedItemId = pickedItemId,
            accessToken = accessToken,
        )
    }
}

/**
 * Thin adapter over Google Play services AuthorizationClient.
 *
 * This class intentionally does not request offline access, a server auth code or a
 * refresh token. Authorization/token caching remains owned by Google Play services.
 */
class GoogleDriveAuthorizationGateway private constructor(
    private val authorizationClient: AuthorizationClient,
) {
    fun authorizeFolderSelection(): Task<AuthorizationResult> =
        authorizationClient.authorize(GoogleDriveAuthorizationRequestFactory.folderPickerRequest())

    fun resultFromIntent(data: Intent): AuthorizationResult =
        authorizationClient.getAuthorizationResultFromIntent(data)

    companion object {
        fun create(context: Context): GoogleDriveAuthorizationGateway =
            GoogleDriveAuthorizationGateway(
                Identity.getAuthorizationClient(context.applicationContext),
            )
    }
}
