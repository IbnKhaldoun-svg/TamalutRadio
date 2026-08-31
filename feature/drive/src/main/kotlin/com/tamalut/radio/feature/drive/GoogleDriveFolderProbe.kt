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
    val parentIds: List<String> = emptyList(),
) {
    val isFolder: Boolean get() = mimeType == GOOGLE_DRIVE_FOLDER_MIME_TYPE
}

data class GoogleDriveDiagnosticPage(
    val pageNumber: Int,
    val items: List<GoogleDriveProbeItem>,
    val hasNextPage: Boolean,
    val incompleteSearch: Boolean,
)

data class GoogleDriveDiagnosticMeasurement(
    val label: String,
    val pageSize: Int,
    val parentFiltered: Boolean,
    val pages: List<GoogleDriveDiagnosticPage>,
) {
    val items: List<GoogleDriveProbeItem>
        get() = pages.flatMap { it.items }

    val itemIds: Set<String>
        get() = items.mapTo(linkedSetOf()) { it.id }

    val anyIncompleteSearch: Boolean
        get() = pages.any { it.incompleteSearch }
}

enum class GoogleDriveDiagnosticVerdict {
    INCOMPLETE_SEARCH,
    SAME_TOKEN_PARENT_RESULTS_DIFFER,
    FORCED_PAGINATION_MISMATCH,
    AUTHORIZED_UNIVERSE_SINGLE_ITEM,
    AUTHORIZED_UNIVERSE_SELECTED_FOLDER_PLUS_SINGLE_CHILD,
    PARENT_QUERY_SPECIFIC_BROADER_UNIVERSE,
    AUTHORIZED_UNIVERSE_DIFFERS_FROM_PARENT_RESULT,
    CONSISTENT_OTHER,
}

data class GoogleDriveFolderProbeReport(
    val selectedFolder: GoogleDriveProbeItem,
    val a1: GoogleDriveDiagnosticMeasurement,
    val a2: GoogleDriveDiagnosticMeasurement,
    val b: GoogleDriveDiagnosticMeasurement,
    val c: GoogleDriveDiagnosticMeasurement,
    val verdict: GoogleDriveDiagnosticVerdict,
) {
    val selectedFolderId: String get() = selectedFolder.id
    val directChildren: List<GoogleDriveProbeItem> get() = a1.items
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

internal data class DriveHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal data class DriveFilesPage(
    val items: List<GoogleDriveProbeItem>,
    val nextPageToken: String?,
    val incompleteSearch: Boolean,
)

internal interface GoogleDriveReadOnlyReader {
    @Throws(IOException::class)
    fun getItem(accessToken: String, itemId: String): GoogleDriveProbeItem

    @Throws(IOException::class)
    fun listPage(
        accessToken: String,
        query: String,
        pageSize: Int,
        pageToken: String?,
    ): DriveFilesPage
}

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

internal class GoogleDriveApiClient internal constructor(
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

    override fun listPage(
        accessToken: String,
        query: String,
        pageSize: Int,
        pageToken: String?,
    ): DriveFilesPage {
        require(accessToken.isNotBlank()) { "Access token is required" }
        require(query.isNotBlank()) { "Drive query is required" }
        require(pageSize in 1..1000) { "Drive pageSize must be between 1 and 1000" }

        val response = transport.get(
            buildFilesListUrl(
                query = query,
                pageSize = pageSize,
                pageToken = pageToken,
            ),
            accessToken,
        )
        requireSuccessfulResponse(response)
        return parseFilesPage(response.body)
    }

    private fun requireSuccessfulResponse(response: DriveHttpResponse) {
        if (response.statusCode !in 200..299) {
            throw GoogleDriveApiException(response.statusCode, parseGoogleErrorReason(response.body))
        }
    }
}

class GoogleDriveFolderProbeRunner internal constructor(
    private val reader: GoogleDriveReadOnlyReader,
) {
    constructor() : this(GoogleDriveApiClient())

    fun probe(accessToken: String, selectedItemId: String): GoogleDriveFolderProbeReport {
        val selectedItem = reader.getItem(accessToken, selectedItemId)
        if (!selectedItem.isFolder) {
            throw GoogleDriveSelectionException()
        }

        val parentQuery = buildParentQuery(selectedItem.id)
        val a1 = readMeasurement(
            accessToken = accessToken,
            label = "A1",
            query = parentQuery,
            pageSize = 1000,
            parentFiltered = true,
        )
        val a2 = readMeasurement(
            accessToken = accessToken,
            label = "A2",
            query = parentQuery,
            pageSize = 1000,
            parentFiltered = true,
        )
        val b = readMeasurement(
            accessToken = accessToken,
            label = "B",
            query = parentQuery,
            pageSize = 1,
            parentFiltered = true,
        )
        val c = readMeasurement(
            accessToken = accessToken,
            label = "C",
            query = DRIVE_VISIBLE_UNIVERSE_QUERY,
            pageSize = 1000,
            parentFiltered = false,
        )

        return GoogleDriveFolderProbeReport(
            selectedFolder = selectedItem,
            a1 = a1,
            a2 = a2,
            b = b,
            c = c,
            verdict = classifyDiagnostic(
                selectedFolderId = selectedItem.id,
                a1 = a1,
                a2 = a2,
                b = b,
                c = c,
            ),
        )
    }

    private fun readMeasurement(
        accessToken: String,
        label: String,
        query: String,
        pageSize: Int,
        parentFiltered: Boolean,
    ): GoogleDriveDiagnosticMeasurement {
        val pages = mutableListOf<GoogleDriveDiagnosticPage>()
        val seenNextTokens = mutableSetOf<String>()
        var pageToken: String? = null
        var pageNumber = 1

        do {
            check(pageNumber <= MAX_DIAGNOSTIC_PAGES) {
                "Drive diagnostic pagination exceeded $MAX_DIAGNOSTIC_PAGES pages"
            }

            val page = reader.listPage(
                accessToken = accessToken,
                query = query,
                pageSize = pageSize,
                pageToken = pageToken,
            )
            val nextPageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
            if (nextPageToken != null) {
                check(seenNextTokens.add(nextPageToken)) {
                    "Drive API repeated a pagination token during measurement $label"
                }
            }

            pages += GoogleDriveDiagnosticPage(
                pageNumber = pageNumber,
                items = page.items,
                hasNextPage = nextPageToken != null,
                incompleteSearch = page.incompleteSearch,
            )

            pageToken = nextPageToken
            pageNumber += 1
        } while (pageToken != null)

        return GoogleDriveDiagnosticMeasurement(
            label = label,
            pageSize = pageSize,
            parentFiltered = parentFiltered,
            pages = pages,
        )
    }

    companion object {
        private const val MAX_DIAGNOSTIC_PAGES = 5000
    }
}

internal const val DRIVE_VISIBLE_UNIVERSE_QUERY = "trashed = false"

internal fun buildParentQuery(folderId: String): String {
    val escapedFolderId = folderId.replace("\\", "\\\\").replace("'", "\\'")
    return "'$escapedFolderId' in parents and trashed = false"
}

internal fun buildItemMetadataUrl(itemId: String): String {
    val fields = "id,name,mimeType,size,parents"
    return buildString {
        append("https://www.googleapis.com/drive/v3/files/")
        append(encodeQueryComponent(itemId))
        append("?fields=")
        append(encodeQueryComponent(fields))
        append("&supportsAllDrives=true")
    }
}

internal fun buildFilesListUrl(
    query: String,
    pageSize: Int,
    pageToken: String?,
): String {
    val fields = "nextPageToken,incompleteSearch,files(id,name,mimeType,size,parents)"
    val parameters = linkedMapOf(
        "q" to query,
        "corpora" to "user",
        "spaces" to "drive",
        "pageSize" to pageSize.toString(),
        "orderBy" to "name_natural",
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

internal fun parseFilesPage(json: String): DriveFilesPage {
    val root = JsonParser.parseString(json).asJsonObject
    val files = root.getAsJsonArray("files")
    val items = files?.map { element ->
        parseDriveItemObject(element.asJsonObject)
    }.orEmpty().filter { it.id.isNotBlank() }

    return DriveFilesPage(
        items = items,
        nextPageToken = root.get("nextPageToken")?.takeIf { !it.isJsonNull }?.asString,
        incompleteSearch = root.get("incompleteSearch")
            ?.takeIf { !it.isJsonNull }
            ?.asBoolean
            ?: false,
    )
}

private fun parseDriveItemObject(file: JsonObject): GoogleDriveProbeItem = GoogleDriveProbeItem(
    id = file.get("id")?.asString.orEmpty(),
    name = file.get("name")?.asString.orEmpty(),
    mimeType = file.get("mimeType")?.asString.orEmpty(),
    sizeBytes = file.get("size")?.takeIf { !it.isJsonNull }?.asLong,
    parentIds = file.getAsJsonArray("parents")
        ?.mapNotNull { parent -> parent.takeIf { !it.isJsonNull }?.asString }
        .orEmpty(),
)

internal fun classifyDiagnostic(
    selectedFolderId: String,
    a1: GoogleDriveDiagnosticMeasurement,
    a2: GoogleDriveDiagnosticMeasurement,
    b: GoogleDriveDiagnosticMeasurement,
    c: GoogleDriveDiagnosticMeasurement,
): GoogleDriveDiagnosticVerdict {
    if (listOf(a1, a2, b, c).any { it.anyIncompleteSearch }) {
        return GoogleDriveDiagnosticVerdict.INCOMPLETE_SEARCH
    }

    val a1Ids = a1.itemIds
    val a2Ids = a2.itemIds
    val bIds = b.itemIds
    val cIds = c.itemIds

    if (a1Ids != a2Ids) {
        return GoogleDriveDiagnosticVerdict.SAME_TOKEN_PARENT_RESULTS_DIFFER
    }
    if (bIds != a1Ids) {
        return GoogleDriveDiagnosticVerdict.FORCED_PAGINATION_MISMATCH
    }

    if (a1Ids.size == 1) {
        if (cIds == a1Ids) {
            return GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_SINGLE_ITEM
        }

        val cWithoutSelectedFolder = cIds - selectedFolderId
        if (cWithoutSelectedFolder == a1Ids && cIds.size == a1Ids.size + 1) {
            return GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_SELECTED_FOLDER_PLUS_SINGLE_CHILD
        }

        if (cWithoutSelectedFolder.containsAll(a1Ids) && cWithoutSelectedFolder.size > a1Ids.size) {
            return GoogleDriveDiagnosticVerdict.PARENT_QUERY_SPECIFIC_BROADER_UNIVERSE
        }

        if (cWithoutSelectedFolder != a1Ids) {
            return GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_DIFFERS_FROM_PARENT_RESULT
        }
    }

    return GoogleDriveDiagnosticVerdict.CONSISTENT_OTHER
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

fun redactDriveId(itemId: String): String = when {
    itemId.length <= 8 -> "••••"
    else -> itemId.take(4) + "…" + itemId.takeLast(4)
}

private fun encodeQueryComponent(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
