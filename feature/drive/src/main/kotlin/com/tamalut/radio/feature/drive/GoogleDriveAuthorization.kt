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
    fun multiFilePickerRequest(): AuthorizationRequest = AuthorizationRequest.builder()
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
            AuthorizationRequest.ResourceParameter.PICKER_ALLOW_MULTIPLE,
            "true",
        )
        .build()

    @Deprecated("Folder enumeration is retired; use multiFilePickerRequest")
    fun folderPickerRequest(): AuthorizationRequest = multiFilePickerRequest()
}

class GoogleDrivePickerGrant internal constructor(
    val pickedItemIds: List<String>,
    val accessToken: String,
) {
    val pickedItemId: String get() = pickedItemIds.first()

    override fun toString(): String =
        "GoogleDrivePickerGrant(pickedItemIds=${pickedItemIds.map(::redactDriveId)}, accessToken=<redacted>)"
}

object GoogleDriveAuthorizationResultParser {
    const val PICKED_FILE_IDS_PARAM = "picked_file_ids"

    internal fun parsePickedItemIds(rawPickedIds: String?): List<String> = rawPickedIds
        ?.split(',')
        ?.asSequence()
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.distinct()
        ?.toList()
        .orEmpty()

    internal fun parsePickedItemId(rawPickedIds: String?): String? =
        parsePickedItemIds(rawPickedIds).firstOrNull()

    fun requirePickerGrant(result: AuthorizationResult): GoogleDrivePickerGrant {
        check(GoogleDriveAuthorizationPolicy.DRIVE_FILE_SCOPE in result.grantedScopes) {
            "Google Drive drive.file scope was not granted"
        }
        val accessToken = result.accessToken?.takeIf(String::isNotBlank)
            ?: error("Google Drive authorization returned no access token")
        val pickedItemIds = parsePickedItemIds(
            result.tokenResponseParams?.getString(PICKED_FILE_IDS_PARAM),
        ).takeIf(List<String>::isNotEmpty)
            ?: error("Google Picker returned no selected file IDs")

        return GoogleDrivePickerGrant(
            pickedItemIds = pickedItemIds,
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
    fun authorizeFileSelection(): Task<AuthorizationResult> =
        authorizationClient.authorize(GoogleDriveAuthorizationRequestFactory.multiFilePickerRequest())

    @Deprecated("Folder enumeration is retired; use authorizeFileSelection")
    fun authorizeFolderSelection(): Task<AuthorizationResult> = authorizeFileSelection()

    fun resultFromIntent(data: Intent): AuthorizationResult =
        authorizationClient.getAuthorizationResultFromIntent(data)

    companion object {
        fun create(context: Context): GoogleDriveAuthorizationGateway =
            GoogleDriveAuthorizationGateway(
                Identity.getAuthorizationClient(context.applicationContext),
            )
    }
}
