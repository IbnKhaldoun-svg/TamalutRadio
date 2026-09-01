package com.tamalut.radio.core.playback

import androidx.media3.common.MimeTypes
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackCompatibilityTest {
    @Test
    fun ordinaryM3u8EndpointIsClassifiedAsHls() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            RadioStreamMimeTypePolicy.mimeTypeFor(
                StationId("rtl-1025"),
                StreamEndpoint("https://example.test/live/playlist.m3u8"),
            ),
        )
    }

    @Test
    fun chadaPlaylistEndpointIsExplicitlyClassifiedAsHlsWithoutM3u8Suffix() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            RadioStreamMimeTypePolicy.mimeTypeFor(
                StationId("chada-fm"),
                StreamEndpoint("https://stream.bodkas.com/playlist?id=chadafmradio"),
            ),
        )
    }

    @Test
    fun directMp3AndAacEndpointsAreNotMisclassifiedAsHls() {
        assertNull(
            RadioStreamMimeTypePolicy.mimeTypeFor(
                StationId("radio-105"),
                StreamEndpoint("https://example.test/live.mp3"),
            ),
        )
        assertNull(
            RadioStreamMimeTypePolicy.mimeTypeFor(
                StationId("heart-uk"),
                StreamEndpoint("https://example.test/live.aac"),
            ),
        )
    }

    @Test
    fun playbackErrorProjectionProducesConciseVisibleMessage() {
        val message = PlaybackErrorProjection.message(2004)
        assertTrue(message.contains("Stream non disponibile"))
        assertTrue(message.contains("2004"))
    }
}
