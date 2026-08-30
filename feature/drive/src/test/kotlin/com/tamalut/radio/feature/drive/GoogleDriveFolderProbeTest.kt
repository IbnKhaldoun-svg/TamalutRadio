package com.tamalut.radio.feature.drive

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveFolderProbeTest {
    @Test
    fun pickedFolderIdParserUsesFirstNonBlankPickerId() {
        assertEquals(
            "folder-123",
            GoogleDriveAuthorizationResultParser.parsePickedFolderId("  folder-123 , folder-456 "),
        )
        assertNull(GoogleDriveAuthorizationResultParser.parsePickedFolderId(" ,  "))
        assertNull(GoogleDriveAuthorizationResultParser.parsePickedFolderId(null))
    }

    @Test
    fun childrenUrlUsesParentQueryAndPaginationWithoutBroadScopeParameters() {
        val url = buildChildrenUrl("folder-123", "page two")

        assertTrue(url.startsWith("https://www.googleapis.com/drive/v3/files?"))
        assertTrue(url.contains("q=%27folder-123%27%20in%20parents%20and%20trashed%20%3D%20false"))
        assertTrue(url.contains("spaces=drive"))
        assertTrue(url.contains("pageSize=1000"))
        assertTrue(url.contains("supportsAllDrives=true"))
        assertTrue(url.contains("includeItemsFromAllDrives=true"))
        assertTrue(url.contains("pageToken=page%20two"))
        assertFalse(url.contains("drive.readonly"))
    }

    @Test
    fun childrenPageParsesFilesFoldersSizesAndNextPageToken() {
        val page = parseChildrenPage(
            """
            {
              "nextPageToken": "next-1",
              "files": [
                {"id":"audio-1","name":"Brano 1.mp3","mimeType":"audio/mpeg","size":"12345"},
                {"id":"folder-1","name":"Sottocartella","mimeType":"application/vnd.google-apps.folder"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("next-1", page.nextPageToken)
        assertEquals(2, page.items.size)
        assertEquals(12345L, page.items[0].sizeBytes)
        assertFalse(page.items[0].isFolder)
        assertTrue(page.items[1].isFolder)
        assertNull(page.items[1].sizeBytes)
    }

    @Test
    fun apiClientPaginatesAndNeverExposesTokenInRequestUrl() {
        val seenUrls = mutableListOf<String>()
        val seenTokens = mutableListOf<String>()
        val transport = DriveHttpTransport { url, token ->
            seenUrls += url
            seenTokens += token
            if (seenUrls.size == 1) {
                DriveHttpResponse(
                    200,
                    """{"nextPageToken":"next","files":[{"id":"one","name":"One","mimeType":"audio/mpeg"}]}""",
                )
            } else {
                DriveHttpResponse(
                    200,
                    """{"files":[{"id":"two","name":"Two","mimeType":"audio/mpeg"}]}""",
                )
            }
        }

        val items = GoogleDriveApiClient(transport).listChildren("secret-token", "root")

        assertEquals(listOf("one", "two"), items.map { it.id })
        assertEquals(listOf("secret-token", "secret-token"), seenTokens)
        assertEquals(2, seenUrls.size)
        assertTrue(seenUrls.none { it.contains("secret-token") })
    }

    @Test
    fun apiClientSurfacesSafeGoogleErrorReason() {
        val client = GoogleDriveApiClient(
            DriveHttpTransport { _, _ ->
                DriveHttpResponse(
                    403,
                    """{"error":{"status":"PERMISSION_DENIED","errors":[{"reason":"insufficientPermissions"}]}}""",
                )
            },
        )

        val error = runCatching { client.listChildren("token", "root") }.exceptionOrNull()

        assertTrue(error is GoogleDriveApiException)
        error as GoogleDriveApiException
        assertEquals(403, error.statusCode)
        assertEquals("insufficientPermissions", error.reason)
        assertFalse(error.message.orEmpty().contains("token"))
    }

    @Test
    fun runnerListsDirectChildrenThenFirstNestedFolder() {
        val calls = mutableListOf<String>()
        val reader = GoogleDriveChildrenReader { _, folderId ->
            calls += folderId
            when (folderId) {
                "root" -> listOf(
                    GoogleDriveProbeItem("song", "Song.mp3", "audio/mpeg", 99L),
                    GoogleDriveProbeItem(
                        "nested",
                        "Sottocartella",
                        GOOGLE_DRIVE_FOLDER_MIME_TYPE,
                        null,
                    ),
                )
                "nested" -> listOf(
                    GoogleDriveProbeItem("deep-song", "Deep.mp3", "audio/mpeg", 100L),
                )
                else -> emptyList()
            }
        }

        val report = GoogleDriveFolderProbeRunner(reader).probe("token", "root")

        assertEquals(listOf("root", "nested"), calls)
        assertEquals(2, report.directChildren.size)
        assertEquals("Sottocartella", report.nestedFolder?.name)
        assertEquals(listOf("Deep.mp3"), report.nestedChildren?.map { it.name })
        assertTrue(report.nestedAccessVerified)
        assertNull(report.nestedError)
    }

    @Test
    fun runnerReportsNestedFailureWithoutDiscardingDirectListing() {
        val reader = GoogleDriveChildrenReader { _, folderId ->
            if (folderId == "root") {
                listOf(
                    GoogleDriveProbeItem(
                        "nested",
                        "Sottocartella",
                        GOOGLE_DRIVE_FOLDER_MIME_TYPE,
                        null,
                    ),
                )
            } else {
                throw IOException("network details that must not be surfaced")
            }
        }

        val report = GoogleDriveFolderProbeRunner(reader).probe("token", "root")

        assertEquals(1, report.directChildren.size)
        assertFalse(report.nestedAccessVerified)
        assertNull(report.nestedChildren)
        assertEquals("Errore di rete durante la lettura della sottocartella", report.nestedError)
    }

    @Test
    fun runnerDoesNotClaimNestedVerificationWhenNoSubfolderExists() {
        val report = GoogleDriveFolderProbeRunner(
            GoogleDriveChildrenReader { _, _ ->
                listOf(GoogleDriveProbeItem("song", "Song.mp3", "audio/mpeg", 1L))
            },
        ).probe("token", "root")

        assertFalse(report.nestedAccessVerified)
        assertNull(report.nestedFolder)
        assertNull(report.nestedChildren)
        assertNull(report.nestedError)
    }

    @Test
    fun redactionNeverShowsWholeFolderId() {
        assertEquals("abcd…wxyz", redactDriveId("abcd1234wxyz"))
        assertEquals("••••", redactDriveId("short"))
    }
}
