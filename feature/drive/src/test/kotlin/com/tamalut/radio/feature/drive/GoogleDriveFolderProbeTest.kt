package com.tamalut.radio.feature.drive

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveFolderProbeTest {
    @Test
    fun pickedItemIdParserUsesFirstNonBlankPickerId() {
        assertEquals(
            "folder-123",
            GoogleDriveAuthorizationResultParser.parsePickedItemId("  folder-123 , folder-456 "),
        )
        assertNull(GoogleDriveAuthorizationResultParser.parsePickedItemId(" ,  "))
        assertNull(GoogleDriveAuthorizationResultParser.parsePickedItemId(null))
    }

    @Test
    fun itemMetadataUrlUsesFilesGetReadOnlyEndpointAndParentsField() {
        val url = buildItemMetadataUrl("folder-123")

        assertTrue(url.startsWith("https://www.googleapis.com/drive/v3/files/folder-123?"))
        assertTrue(url.contains("fields=id%2Cname%2CmimeType%2Csize%2Cparents"))
        assertTrue(url.contains("supportsAllDrives=true"))
        assertEquals(GoogleDriveReadOperation.FILES_GET, GoogleDriveReadOnlyPolicy.requireAllowedGet(url))
    }

    @Test
    fun parentQueryEscapesFolderId() {
        assertEquals(
            "'folder\\'\\\\id' in parents and trashed = false",
            buildParentQuery("folder'\\id"),
        )
    }

    @Test
    fun parentListUrlIncludesDiagnosticPaginationOrderingAndIncompleteSearchFields() {
        val url = buildFilesListUrl(
            query = buildParentQuery("folder-123"),
            pageSize = 1,
            pageToken = "page two",
        )

        assertTrue(url.startsWith("https://www.googleapis.com/drive/v3/files?"))
        assertTrue(url.contains("q=%27folder-123%27%20in%20parents%20and%20trashed%20%3D%20false"))
        assertTrue(url.contains("corpora=user"))
        assertTrue(url.contains("spaces=drive"))
        assertTrue(url.contains("pageSize=1"))
        assertTrue(url.contains("orderBy=name_natural"))
        assertTrue(url.contains("nextPageToken%2CincompleteSearch%2Cfiles%28id%2Cname%2CmimeType%2Csize%2Cparents%29"))
        assertTrue(url.contains("supportsAllDrives=true"))
        assertTrue(url.contains("includeItemsFromAllDrives=true"))
        assertTrue(url.contains("pageToken=page%20two"))
        assertFalse(url.contains("drive.readonly"))
        assertEquals(GoogleDriveReadOperation.FILES_LIST, GoogleDriveReadOnlyPolicy.requireAllowedGet(url))
    }

    @Test
    fun authorizedUniverseUrlHasNoParentPredicate() {
        val url = buildFilesListUrl(
            query = DRIVE_VISIBLE_UNIVERSE_QUERY,
            pageSize = 1000,
            pageToken = null,
        )

        assertTrue(url.contains("q=trashed%20%3D%20false"))
        assertTrue(url.contains("pageSize=1000"))
        assertTrue(url.contains("corpora=user"))
        assertTrue(url.contains("orderBy=name_natural"))
        assertFalse(url.contains("in%20parents"))
    }

    @Test
    fun filesPageParsesParentsIncompleteSearchAndNextToken() {
        val page = parseFilesPage(
            """
            {
              "nextPageToken": "next-1",
              "incompleteSearch": true,
              "files": [
                {
                  "id":"audio-1",
                  "name":"Brano 1.mp3",
                  "mimeType":"audio/mpeg",
                  "size":"12345",
                  "parents":["folder-1"]
                },
                {
                  "id":"folder-2",
                  "name":"Sottocartella",
                  "mimeType":"application/vnd.google-apps.folder",
                  "parents":["folder-1"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("next-1", page.nextPageToken)
        assertTrue(page.incompleteSearch)
        assertEquals(2, page.items.size)
        assertEquals(listOf("folder-1"), page.items[0].parentIds)
        assertEquals(12345L, page.items[0].sizeBytes)
        assertTrue(page.items[1].isFolder)
        assertNull(page.items[1].sizeBytes)
    }

    @Test
    fun apiClientNeverExposesTokenInUrlsAndUsesOnlyReadEndpoints() {
        val seenUrls = mutableListOf<String>()
        val seenTokens = mutableListOf<String>()
        val transport = DriveReadOnlyHttpTransport { url, token ->
            seenUrls += url
            seenTokens += token
            if ("/drive/v3/files/root?" in url) {
                DriveHttpResponse(
                    200,
                    """{"id":"root","name":"Cartella test","mimeType":"application/vnd.google-apps.folder"}""",
                )
            } else {
                DriveHttpResponse(
                    200,
                    """{"files":[{"id":"song","name":"Song.mp3","mimeType":"audio/mpeg","parents":["root"]}]}""",
                )
            }
        }

        val client = GoogleDriveApiClient(transport)
        val selected = client.getItem("secret-token", "root")
        val page = client.listPage("secret-token", buildParentQuery("root"), 1000, null)

        assertTrue(selected.isFolder)
        assertEquals(listOf("song"), page.items.map { it.id })
        assertEquals(listOf("secret-token", "secret-token"), seenTokens)
        assertTrue(seenUrls.none { it.contains("secret-token") })
        assertTrue(seenUrls.all { GoogleDriveReadOnlyPolicy.requireAllowedGet(it) in GoogleDriveReadOnlyPolicy.allowedOperations })
    }

    @Test
    fun runnerExecutesA1A2BAndCWithSameTokenAndFollowsEmptyIntermediatePage() {
        val calls = mutableListOf<ListCall>()
        val reader = object : GoogleDriveReadOnlyReader {
            override fun getItem(accessToken: String, itemId: String): GoogleDriveProbeItem {
                assertEquals("same-token", accessToken)
                return GoogleDriveProbeItem(
                    id = itemId,
                    name = "TamalutRadio Test",
                    mimeType = GOOGLE_DRIVE_FOLDER_MIME_TYPE,
                    sizeBytes = null,
                )
            }

            override fun listPage(
                accessToken: String,
                query: String,
                pageSize: Int,
                pageToken: String?,
            ): DriveFilesPage {
                calls += ListCall(accessToken, query, pageSize, pageToken)
                assertEquals("same-token", accessToken)

                return when {
                    query == buildParentQuery("root") && pageSize == 1000 -> DriveFilesPage(
                        items = listOf(song("song-1", "A.mp3", "root")),
                        nextPageToken = null,
                        incompleteSearch = false,
                    )
                    query == buildParentQuery("root") && pageSize == 1 && pageToken == null -> DriveFilesPage(
                        items = emptyList(),
                        nextPageToken = "forced-next",
                        incompleteSearch = false,
                    )
                    query == buildParentQuery("root") && pageSize == 1 && pageToken == "forced-next" -> DriveFilesPage(
                        items = listOf(song("song-1", "A.mp3", "root")),
                        nextPageToken = null,
                        incompleteSearch = false,
                    )
                    query == DRIVE_VISIBLE_UNIVERSE_QUERY -> DriveFilesPage(
                        items = listOf(
                            GoogleDriveProbeItem(
                                id = "root",
                                name = "TamalutRadio Test",
                                mimeType = GOOGLE_DRIVE_FOLDER_MIME_TYPE,
                                sizeBytes = null,
                            ),
                            song("song-1", "A.mp3", "root"),
                        ),
                        nextPageToken = null,
                        incompleteSearch = false,
                    )
                    else -> error("Unexpected call: $query pageSize=$pageSize token=$pageToken")
                }
            }
        }

        val report = GoogleDriveFolderProbeRunner(reader).probe("same-token", "root")

        assertEquals("TamalutRadio Test", report.selectedFolder.name)
        assertEquals(1, report.a1.items.size)
        assertEquals(1, report.a2.items.size)
        assertEquals(2, report.b.pages.size)
        assertTrue(report.b.pages[0].items.isEmpty())
        assertTrue(report.b.pages[0].hasNextPage)
        assertEquals(setOf("song-1"), report.b.itemIds)
        assertEquals(setOf("root", "song-1"), report.c.itemIds)
        assertEquals(
            GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_SELECTED_FOLDER_PLUS_SINGLE_CHILD,
            report.verdict,
        )
        assertEquals(5, calls.size)
        assertTrue(calls.all { it.accessToken == "same-token" })
        assertEquals(listOf(1000, 1000, 1, 1, 1000), calls.map { it.pageSize })
    }

    @Test
    fun classificationDetectsSameTokenParentInconsistency() {
        val a1 = measurement("A1", listOf("one"))
        val a2 = measurement("A2", listOf("two"))
        val b = measurement("B", listOf("one"))
        val c = measurement("C", listOf("one"), parentFiltered = false)

        assertEquals(
            GoogleDriveDiagnosticVerdict.SAME_TOKEN_PARENT_RESULTS_DIFFER,
            classifyDiagnostic("root", a1, a2, b, c),
        )
    }

    @Test
    fun classificationDetectsForcedPaginationMismatch() {
        val a1 = measurement("A1", listOf("one"))
        val a2 = measurement("A2", listOf("one"))
        val b = measurement("B", listOf("two"))
        val c = measurement("C", listOf("one"), parentFiltered = false)

        assertEquals(
            GoogleDriveDiagnosticVerdict.FORCED_PAGINATION_MISMATCH,
            classifyDiagnostic("root", a1, a2, b, c),
        )
    }

    @Test
    fun classificationDetectsExactSingleItemAuthorizedUniverse() {
        val a1 = measurement("A1", listOf("one"))
        val a2 = measurement("A2", listOf("one"))
        val b = measurement("B", listOf("one"))
        val c = measurement("C", listOf("one"), parentFiltered = false)

        assertEquals(
            GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_SINGLE_ITEM,
            classifyDiagnostic("root", a1, a2, b, c),
        )
    }

    @Test
    fun classificationDetectsBroaderUniverseBeyondSelectedFolderAndParentChild() {
        val a1 = measurement("A1", listOf("one"))
        val a2 = measurement("A2", listOf("one"))
        val b = measurement("B", listOf("one"))
        val c = measurement(
            label = "C",
            ids = listOf("root", "one", "other"),
            parentFiltered = false,
        )

        assertEquals(
            GoogleDriveDiagnosticVerdict.PARENT_QUERY_SPECIFIC_BROADER_UNIVERSE,
            classifyDiagnostic("root", a1, a2, b, c),
        )
    }

    @Test
    fun classificationDoesNotOverclaimWhenAuthorizedUniverseDiffersRatherThanSupersets() {
        val a1 = measurement("A1", listOf("one"))
        val a2 = measurement("A2", listOf("one"))
        val b = measurement("B", listOf("one"))
        val c = measurement("C", listOf("other"), parentFiltered = false)

        assertEquals(
            GoogleDriveDiagnosticVerdict.AUTHORIZED_UNIVERSE_DIFFERS_FROM_PARENT_RESULT,
            classifyDiagnostic("root", a1, a2, b, c),
        )
    }

    @Test
    fun incompleteSearchMakesVerdictInconclusiveBeforeSetClaims() {
        val a1 = measurement("A1", listOf("one"), incompleteSearch = true)
        val a2 = measurement("A2", listOf("one"))
        val b = measurement("B", listOf("one"))
        val c = measurement("C", listOf("one"), parentFiltered = false)

        assertEquals(
            GoogleDriveDiagnosticVerdict.INCOMPLETE_SEARCH,
            classifyDiagnostic("root", a1, a2, b, c),
        )
    }

    @Test
    fun runnerRejectsNonFolderSelectionBeforeAnyListCall() {
        var listCalled = false
        val reader = object : GoogleDriveReadOnlyReader {
            override fun getItem(accessToken: String, itemId: String) =
                GoogleDriveProbeItem(itemId, "Song.mp3", "audio/mpeg", 42L)

            override fun listPage(
                accessToken: String,
                query: String,
                pageSize: Int,
                pageToken: String?,
            ): DriveFilesPage {
                listCalled = true
                return DriveFilesPage(emptyList(), null, false)
            }
        }

        val error = runCatching {
            GoogleDriveFolderProbeRunner(reader).probe("token", "audio-file")
        }.exceptionOrNull()

        assertTrue(error is GoogleDriveSelectionException)
        assertFalse(listCalled)
    }

    @Test
    fun apiClientSurfacesSafeGoogleErrorReason() {
        val client = GoogleDriveApiClient(
            DriveReadOnlyHttpTransport { _, _ ->
                DriveHttpResponse(
                    403,
                    """{"error":{"status":"PERMISSION_DENIED","errors":[{"reason":"insufficientPermissions"}]}}""",
                )
            },
        )

        val error = runCatching { client.getItem("token", "root") }.exceptionOrNull()

        assertTrue(error is GoogleDriveApiException)
        error as GoogleDriveApiException
        assertEquals(403, error.statusCode)
        assertEquals("insufficientPermissions", error.reason)
        assertFalse(error.message.orEmpty().contains("token"))
    }

    @Test
    fun redactionNeverShowsWholeItemId() {
        assertEquals("abcd…wxyz", redactDriveId("abcd1234wxyz"))
        assertEquals("••••", redactDriveId("short"))
    }

    private data class ListCall(
        val accessToken: String,
        val query: String,
        val pageSize: Int,
        val pageToken: String?,
    )

    private fun song(id: String, name: String, parent: String) = GoogleDriveProbeItem(
        id = id,
        name = name,
        mimeType = "audio/mpeg",
        sizeBytes = 1L,
        parentIds = listOf(parent),
    )

    private fun measurement(
        label: String,
        ids: List<String>,
        parentFiltered: Boolean = true,
        incompleteSearch: Boolean = false,
    ): GoogleDriveDiagnosticMeasurement = GoogleDriveDiagnosticMeasurement(
        label = label,
        pageSize = if (label == "B") 1 else 1000,
        parentFiltered = parentFiltered,
        pages = listOf(
            GoogleDriveDiagnosticPage(
                pageNumber = 1,
                items = ids.map { song(it, "$it.mp3", "root") },
                hasNextPage = false,
                incompleteSearch = incompleteSearch,
            ),
        ),
    )
}
