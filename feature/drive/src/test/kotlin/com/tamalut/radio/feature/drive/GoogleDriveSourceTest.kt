package com.tamalut.radio.feature.drive

import com.tamalut.radio.core.cloud.CloudFolderId
import com.tamalut.radio.core.cloud.CloudMusicTrack
import com.tamalut.radio.core.model.MediaId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveSourceTest {
    @Test
    fun `Google Drive is the first concrete cloud source and delegates folder reads`() = runTest {
        val expected = listOf(
            CloudMusicTrack(
                mediaId = MediaId("drive:file-1"),
                remoteId = "file-1",
                title = "Track 1.mp3",
                mimeType = "audio/mpeg",
            ),
        )
        var receivedFolder: CloudFolderId? = null
        val source = GoogleDriveSource(
            GoogleDriveFolderReader { folderId ->
                receivedFolder = folderId
                expected
            },
        )

        val actual = source.listMusic(CloudFolderId("folder-1"))

        assertEquals("google-drive", source.providerId.value)
        assertEquals("folder-1", receivedFolder?.value)
        assertEquals(expected, actual)
    }
}
