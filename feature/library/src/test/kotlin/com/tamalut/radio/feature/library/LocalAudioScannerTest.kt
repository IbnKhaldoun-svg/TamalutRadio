package com.tamalut.radio.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioScannerTest {
    @Test
    fun recursiveCollectionKeepsOnlyAudioSortsAndSkipsUnreadableChildFolder() {
        val entries = mapOf(
            "root" to listOf(
                directory("album"),
                file("z", "Zulu.mp3", "audio/mpeg"),
                file("note", "notes.txt", "text/plain"),
            ),
            "album" to listOf(
                directory("nested"),
                directory("blocked"),
                file("b", "Beta.flac", "application/octet-stream"),
            ),
            "nested" to listOf(
                file("a", "Alpha.ogg", "audio/ogg"),
            ),
        )

        val tracks = collectAudioTracks("root") { parentId ->
            if (parentId == "blocked") {
                throw SecurityException("child permission revoked")
            }
            entries[parentId].orEmpty()
        }

        assertEquals(listOf("Alpha", "Beta", "Zulu"), tracks.map { it.title })
        assertEquals(
            listOf("content://test/a", "content://test/b", "content://test/z"),
            tracks.map { it.contentUri },
        )
    }

    @Test
    fun rootAccessFailureIsNotSilenced() {
        var failure: Throwable? = null
        try {
            collectAudioTracks("root") {
                throw SecurityException("root permission revoked")
            }
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is SecurityException)
        assertEquals("root permission revoked", failure?.message)
    }

    @Test
    fun audioFilterUsesMimeFirstAndOnlySafeExtensionFallback() {
        assertTrue(isAudioDocument("track.bin", "audio/mpeg"))
        assertTrue(isAudioDocument("track.opus", "application/octet-stream"))
        assertTrue(isAudioDocument("track.flac", null))
        assertFalse(isAudioDocument("fake.mp3", "video/mp4"))
        assertFalse(isAudioDocument("cover.jpg", "image/jpeg"))
        assertFalse(isAudioDocument("README", null))
    }

    private fun directory(id: String) = DocumentEntry(
        documentId = id,
        displayName = id,
        mimeType = "vnd.android.document/directory",
        contentUri = "content://test/$id",
        isDirectory = true,
    )

    private fun file(id: String, name: String, mimeType: String?) = DocumentEntry(
        documentId = id,
        displayName = name,
        mimeType = mimeType,
        contentUri = "content://test/$id",
        isDirectory = false,
    )
}
