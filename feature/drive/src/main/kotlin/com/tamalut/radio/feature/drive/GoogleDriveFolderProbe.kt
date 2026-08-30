package com.tamalut.radio.feature.drive

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal const val GOOGLE_DRIVE_FOLDER_MIME_TYPE = GoogleDriveAuthorizationPolicy.DRIVE_FOLDER_MIME_TYPE

data class GoogleDriveProbeItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
) {
    val isFolder: Boolean get() = mimeType == GOOGLE_DRIVE_FOLDER_MIME_TYPE
}

data class GoogleDriveFolderProbeReport(
    val selectedFolderId: String,
    val directChildren: List<GoogleDriveProbeItem>,
    val nestedFolder: GoogleDriveProbeItem?,
    val nestedChildren: List<GoogleDriveProbeItem>?,
    val nestedError: String?,
) {
    val nestedAccessVerified: Boolean
        get() = nestedFolder != null && nestedChildren != null && nestedError == null
}

class GoogleDriveApiException(
    val statusCode: Int,
    val reason: String?,
) : IOException(
    buildString {
        append("Google Drive API HTTP ")
        append(statusCode)
        if (!reason.isNullOrBlank()) {
            append(" (")
            append(reason)
            append(')')
        }
    },
)

class GoogleDriveSelectionException : IOException(
    "La selezione Google Drive non è una cartella. Scegli una cartella e riprova.",
)

interface GoogleDriveReadOnlyReader {
    @Throws(IOException::class)
    fun getItem(accessToken: String, itemId: String): GoogleDriveProbeItem

    @Throws(IOException::class)
    fun listChildren(accessToken: String, folderId: String): List<GoogleDriveProbeItem>
}

internal data class DriveHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal fun interface DriveReadOnlyHttpTransport {
    @Throws(IOException::class)
    fun get(url: String, accessToken: String): DriveHttpResponse
}

internal class UrlConnectionDriveReadOnlyHttpTransport : DriveReadOnlyHttpTransport {
    override fun get(url: String, accessToken: String): DriveHttpResponse {
        GoogleDriveReadOnlyPolicy.requireAllowedGet(url)
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            DriveHttpResponse(statusCode, body)
        } finally {
            connection.disconnect()
        }
    }
}

class GoogleDriveApiClient internal constructor(
    private val transport: DriveReadOnlyHttpTransport,
) : GoogleDriveReadOnlyReader {
    constructor() : this(UrlConnectionDriveReadOnlyHttpTransport())

    override fun getItem(accessToken: String, itemId: String): GoogleDriveProbeItem {
        require(accessToken.isNotBlank()) { "Access token is required" }
        require(itemId.isNotBlank()) { "Drive item ID is required" }

        val response = transport.get(buildItemMetadataUrl(itemId), accessToken)
        requireSuccessfulResponse(response)
        return parseDriveItem(response.body)
    }

    override fun listChildren(accessToken: String, folderId: String): List<GoogleDriveProbeItem> {
        require(accessToken.isNotBlank()) { "Access token is required" }
        require(folderId.isNotBlank()) { "Folder ID is required" }

        val children = mutableListOf<GoogleDriveProbeItem>()
        var pageToken: String? = null
        var pageCount = 0

        do {
            check(pageCount++ < MAX_PAGES) { "Drive pagination exceeded $MAX_PAGES pages" }
            val response = transport.get(buildChildrenUrl(folderId, pageToken), accessToken)
            requireSuccessfulResponse(response)
            val page = parseChildrenPage(response.body)
            children += page.items
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrBlank())

        return children
    }

    private fun requireSuccessfulResponse(response: DriveHttpResponse) {
        if (response.statusCode !in 200..299) {
            throw GoogleDriveApiException(response.statusCode, parseGoogleErrorReason(response.body))
        }
    }

    companion object {
        private const val MAX_PAGES = 100
    }
}

class GoogleDriveFolderProbeRunner(
    private val reader: GoogleDriveReadOnlyReader,
) {
    fun probe(accessToken: String, selectedItemId: String): GoogleDriveFolderProbeReport {
        val selectedItem = reader.getItem(accessToken, selectedItemId)
        if (!selectedItem.isFolder) {
            throw GoogleDriveSelectionException()
        }

        val directChildren = reader.listChildren(accessToken, selectedItem.id)
        val nestedFolder = directChildren.firstOrNull { it.isFolder }

        if (nestedFolder == null) {
            return GoogleDriveFolderProbeReport(
                selectedFolderId = selectedItem.id,
                directChildren = directChildren,
                nestedFolder = null,
                nestedChildren = null,
                nestedError = null,
            )
        }

        return try {
            val nestedChildren = reader.listChildren(accessToken, nestedFolder.id)
            GoogleDriveFolderProbeReport(
                selectedFolderId = selectedItem.id,
                directChildren = directChildren,
                nestedFolder = nestedFolder,
                nestedChildren = nestedChildren,
                nestedError = null,
            )
        } catch (error: Exception) {
            GoogleDriveFolderProbeReport(
                selectedFolderId = selectedItem.id,
                directChildren = directChildren,
                nestedFolder = nestedFolder,
                nestedChildren = null,
                nestedError = error.toSafeProbeMessage(),
            )
        }
    }
}

internal data class DriveChildrenPage(
    val items: List<GoogleDriveProbeItem>,
    val nextPageToken: String?,
)

internal fun buildItemMetadataUrl(itemId: String): String {
    val fields = "id,name,mimeType,size"
    return buildString {
        append("https://www.googleapis.com/drive/v3/files/")
        append(encodeQueryComponent(itemId))
        append("?fields=")
        append(encodeQueryComponent(fields))
        append("&supportsAllDrives=true")
    }
}

internal fun buildChildrenUrl(folderId: String, pageToken: String?): String {
    val escapedFolderId = folderId.replace("\\", "\\\\").replace("'", "\\'")
    val query = "'$escapedFolderId' in parents and trashed = false"
    val fields = "nextPageToken,files(id,name,mimeType,size)"
    val parameters = linkedMapOf(
        "q" to query,
        "spaces" to "drive",
        "pageSize" to "1000",
        "fields" to fields,
        "supportsAllDrives" to "true",
        "includeItemsFromAllDrives" to "true",
    )
    if (!pageToken.isNullOrBlank()) parameters["pageToken"] = pageToken

    return buildString {
        append("https://www.googleapis.com/drive/v3/files?")
        append(
            parameters.entries.joinToString("&") { (key, value) ->
                "${encodeQueryComponent(key)}=${encodeQueryComponent(value)}"
            },
        )
    }
}

internal fun parseDriveItem(json: String): GoogleDriveProbeItem =
    parseDriveItemObject(JsonParser.parseString(json).asJsonObject)
        .also { check(it.id.isNotBlank()) { "Drive files.get returned no item ID" } }

internal fun parseChildrenPage(json: String): DriveChildrenPage {
    val root = JsonParser.parseString(json).asJsonObject
    val files = root.getAsJsonArray("files")
    val items = files?.map { element ->
        parseDriveItemObject(element.asJsonObject)
    }.orEmpty().filter { it.id.isNotBlank() }

    return DriveChildrenPage(
        items = items,
        nextPageToken = root.get("nextPageToken")?.takeIf { !it.isJsonNull }?.asString,
    )
}

private fun parseDriveItemObject(file: JsonObject): GoogleDriveProbeItem = GoogleDriveProbeItem(
    id = file.get("id")?.asString.orEmpty(),
    name = file.get("name")?.asString.orEmpty(),
    mimeType = file.get("mimeType")?.asString.orEmpty(),
    sizeBytes = file.get("size")?.takeIf { !it.isJsonNull }?.asLong,
)

internal fun parseGoogleErrorReason(json: String): String? = runCatching {
    val error = JsonParser.parseString(json).asJsonObject.getAsJsonObject("error")
    error?.getAsJsonArray("errors")
        ?.firstOrNull()
        ?.asJsonObject
        ?.get("reason")
        ?.asString
        ?: error?.get("status")?.asString
}.getOrNull()

fun redactDriveId(folderId: String): String = when {
    folderId.length <= 8 -> "••••"
    else -> folderId.take(4) + "…" + folderId.takeLast(4)
}

private fun encodeQueryComponent(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

private fun Throwable.toSafeProbeMessage(): String = when (this) {
    is GoogleDriveApiException -> message ?: "Errore Google Drive API"
    is IOException -> "Errore di rete durante la lettura della sottocartella"
    else -> "Errore durante la verifica della sottocartella"
}
