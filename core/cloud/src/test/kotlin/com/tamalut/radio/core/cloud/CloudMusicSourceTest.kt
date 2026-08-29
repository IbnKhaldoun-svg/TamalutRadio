package com.tamalut.radio.core.cloud

import com.tamalut.radio.core.model.MediaId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CloudMusicSourceTest {
    @Test
    fun `provider and folder IDs reject blank values`() {
        assertFailsWith<IllegalArgumentException> { CloudProviderId(" ") }
        assertFailsWith<IllegalArgumentException> { CloudFolderId("") }
    }

    @Test
    fun `cloud track keeps provider remote identity separate from shared media id`() {
        val track = CloudMusicTrack(
            mediaId = MediaId("drive:file-123"),
            remoteId = "file-123",
            title = "Atlas Song.mp3",
            mimeType = "audio/mpeg",
        )

        assertEquals("drive:file-123", track.mediaId.value)
        assertEquals("file-123", track.remoteId)
        assertEquals("Atlas Song.mp3", track.title)
    }
}
