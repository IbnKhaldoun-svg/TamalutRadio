package com.tamalut.radio.feature.drive

import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal const val GOOGLE_DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

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

fun interface GoogleDriveChildrenReader {
    @Throws(IOException::class)
    fun listChildren(accessToken: String, folderId: String): List<GoogleDriveProbeItem>
}

internal data class DriveHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal fun interface DriveHttpTransport {
    @Throws(IOException::class)
    fun get(url: String, accessToken: String): DriveHttpResponse
}

internal class UrlConnectionDriveHttpTransport : DriveHttpTransport {
    override fun get(url: String, accessToken: String): DriveHttpResponse {
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
    private val transport: DriveHttpTransport,
) : GoogleDriveChildrenReader {
    constructor() : this(UrlConnectionDriveHttpTransport())

    override fun listChildren(accessToken: String, folderId: String): List<GoogleDriveProbeItem> {
        require(accessToken.isNotBlank()) { "Access token is required" }
        require(folderId.isNotBlank()) { "Folder ID is required" }

        val children = mutableListOf<GoogleDriveProbeItem>()
        var pageToken: String? = null
        var pageCount = 0

        do {
            check(pageCount++ < MAX_PAGES) { "Drive pagination exceeded $MAX_PAGES pages" }
            val response = transport.get(buildChildrenUrl(folderId, pageToken), accessToken)
            if (response.statusCode !in 200..299) {
                throw GoogleDriveApiException(response.statusCode, parseGoogleErrorReason(response.body))
            }
            val page = parseChildrenPage(response.body)
            children += page.items
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrBlank())

        return children
    }

    companion object {
        private const val MAX_PAGES = 100
    }
}

class GoogleDriveFolderProbeRunner(
    private val childrenReader: GoogleDriveChildrenReader,
) {
    fun probe(accessToken: String, selectedFolderId: String): GoogleDriveFolderProbeReport {
        val directChildren = childrenReader.listChildren(accessToken, selectedFolderId)
        val nestedFolder = directChildren.firstOrNull { it.isFolder }

        if (nestedFolder == null) {
            return GoogleDriveFolderProbeReport(
                selectedFolderId = selectedFolderId,
                directChildren = directChildren,
                nestedFolder = null,
                nestedChildren = null,
                nestedError = null,
            )
        }

        return try {
            val nestedChildren = childrenReader.listChildren(accessToken, nestedFolder.id)
            GoogleDriveFolderProbeReport(
                selectedFolderId = selectedFolderId,
                directChildren = directChildren,
                nestedFolder = nestedFolder,
                nestedChildren = nestedChildren,
                nestedError = null,
            )
        } catch (error: Exception) {
            GoogleDriveFolderProbeReport(
                selectedFolderId = selectedFolderId,
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

internal fun parseChildrenPage(json: String): DriveChildrenPage {
    val root = JsonParser.parseString(json).asJsonObject
    val files = root.getAsJsonArray("files")
    val items = files?.map { element ->
        val file = element.asJsonObject
        GoogleDriveProbeItem(
            id = file.get("id")?.asString.orEmpty(),
            name = file.get("name")?.asString.orEmpty(),
            mimeType = file.get("mimeType")?.asString.orEmpty(),
            sizeBytes = file.get("size")?.takeIf { !it.isJsonNull }?.asLong,
        )
    }.orEmpty().filter { it.id.isNotBlank() }

    return DriveChildrenPage(
        items = items,
        nextPageToken = root.get("nextPageToken")?.takeIf { !it.isJsonNull }?.asString,
    )
}

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
