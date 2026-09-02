package com.tamalut.radio.feature.radio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStreamValidatorTest {
    @Test
    fun syntaxGateRejectsCleartextRelativeAndMissingHostBeforeProbe() = runTest {
        val probe = ScriptedProbe()
        val validator = HttpsRadioStreamValidator(probe)

        listOf(
            "http://example.com/live",
            "/relative/live",
            "https:///missing-host",
        ).forEach { candidate ->
            val error = captureFailure { validator.validate(candidate) }
            assertTrue(error is IllegalArgumentException)
        }
        assertTrue(probe.calls.isEmpty())
    }

    @Test
    fun acceptsDirectHttpsSuccessAndNormalizesDefaultPort() = runTest {
        val probe = ScriptedProbe(
            "https://example.com/live" to RadioProbeResponse(
                statusCode = 200,
                contentType = "audio/mpeg",
            ),
        )
        HttpsRadioStreamValidator(probe).validate(" HTTPS://Example.COM:443/live ")
        assertEquals(listOf("https://example.com/live"), probe.calls)
    }

    @Test
    fun followsRelativeHttpsRedirectAndRejectsHttpsToHttpDowngrade() = runTest {
        val successProbe = ScriptedProbe(
            "https://example.com/start" to RadioProbeResponse(302, location = "/final"),
            "https://example.com/final" to RadioProbeResponse(200, contentType = "audio/aac"),
        )
        HttpsRadioStreamValidator(successProbe).validate("https://example.com/start")
        assertEquals(
            listOf("https://example.com/start", "https://example.com/final"),
            successProbe.calls,
        )

        val downgradeProbe = ScriptedProbe(
            "https://example.com/start" to RadioProbeResponse(
                302,
                location = "http://example.com/final",
            ),
        )
        val error = captureFailure {
            HttpsRadioStreamValidator(downgradeProbe).validate("https://example.com/start")
        }
        assertTrue(error.message.orEmpty().contains("HTTPS"))
        assertEquals(listOf("https://example.com/start"), downgradeProbe.calls)
    }

    @Test
    fun rejectsRedirectLoopExcessAndNonSuccessStatus() = runTest {
        val loopProbe = ScriptedProbe(
            "https://example.com/a" to RadioProbeResponse(302, location = "/b"),
            "https://example.com/b" to RadioProbeResponse(302, location = "/a"),
        )
        val loopError = captureFailure {
            HttpsRadioStreamValidator(loopProbe).validate("https://example.com/a")
        }
        assertTrue(loopError.message.orEmpty().contains("ciclo"))

        val excessProbe = ScriptedProbe(
            "https://example.com/0" to RadioProbeResponse(302, location = "/1"),
            "https://example.com/1" to RadioProbeResponse(302, location = "/2"),
        )
        val excessError = captureFailure {
            HttpsRadioStreamValidator(excessProbe, maxRedirects = 1).validate("https://example.com/0")
        }
        assertTrue(excessError.message.orEmpty().contains("Troppi redirect"))

        val unavailable = ScriptedProbe(
            "https://example.com/live" to RadioProbeResponse(503, contentType = "text/plain"),
        )
        val statusError = captureFailure {
            HttpsRadioStreamValidator(unavailable).validate("https://example.com/live")
        }
        assertTrue(statusError.message.orEmpty().contains("HTTP 503"))
    }

    @Test
    fun rejectsCleartextReferenceInHlsAndAcceptsHttpsOrRelativeChildren() = runTest {
        val unsafeProbe = ScriptedProbe(
            "https://example.com/master.m3u8" to RadioProbeResponse(
                statusCode = 200,
                contentType = "application/vnd.apple.mpegurl",
                bodyPrefix = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=96000\nhttp://cdn.example.com/child.m3u8\n",
            ),
        )
        val error = captureFailure {
            HttpsRadioStreamValidator(unsafeProbe).validate("https://example.com/master.m3u8")
        }
        assertTrue(error.message.orEmpty().contains("HTTP non sicuro"))

        val safeProbe = ScriptedProbe(
            "https://example.com/master.m3u8" to RadioProbeResponse(
                statusCode = 200,
                contentType = "application/vnd.apple.mpegurl",
                bodyPrefix = "#EXTM3U\nchild.m3u8\nhttps://cdn.example.com/segment.ts\n",
            ),
        )
        HttpsRadioStreamValidator(safeProbe).validate("https://example.com/master.m3u8")
        assertEquals(1, safeProbe.calls.size)
    }

    @Test
    fun extm3uBodyIdentifiesHlsEvenWithoutHelpfulContentType() = runTest {
        val probe = ScriptedProbe(
            "https://example.com/live" to RadioProbeResponse(
                statusCode = 200,
                contentType = "text/plain",
                bodyPrefix = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"http://keys.example.com/key\"\n",
            ),
        )
        val error = captureFailure {
            HttpsRadioStreamValidator(probe).validate("https://example.com/live")
        }
        assertTrue(error.message.orEmpty().contains("HTTP non sicuro"))
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
        return try {
            block()
            throw AssertionError("Expected failure")
        } catch (error: Throwable) {
            if (error is AssertionError) throw error
            error
        }
    }

    private class ScriptedProbe(
        vararg responses: Pair<String, RadioProbeResponse>,
    ) : RadioHttpProbe {
        private val scripted = responses.toMap()
        val calls = mutableListOf<String>()

        override suspend fun execute(url: String): RadioProbeResponse {
            calls += url
            return scripted[url] ?: error("No scripted response for $url")
        }
    }
}
