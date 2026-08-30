package com.tamalut.radio.feature.drive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveReadOnlyBoundaryTest {
    @Test
    fun `Drive implementation whitelist admits only GET files list and files get`() {
        assertEquals(setOf("GET"), GoogleDriveReadOnlyPolicy.allowedHttpMethods)
        assertEquals(
            setOf(GoogleDriveReadOperation.FILES_LIST, GoogleDriveReadOperation.FILES_GET),
            GoogleDriveReadOnlyPolicy.allowedOperations,
        )

        assertEquals(
            GoogleDriveReadOperation.FILES_LIST,
            GoogleDriveReadOnlyPolicy.requireAllowedGet(
                "https://www.googleapis.com/drive/v3/files?q=x",
            ),
        )
        assertEquals(
            GoogleDriveReadOperation.FILES_GET,
            GoogleDriveReadOnlyPolicy.requireAllowedGet(
                "https://www.googleapis.com/drive/v3/files/abc123?fields=id%2Cname%2CmimeType",
            ),
        )
        assertTrue(
            runCatching {
                GoogleDriveReadOnlyPolicy.requireAllowedGet(
                    "https://www.googleapis.com/drive/v3/files/abc123/permissions",
                )
            }.isFailure,
        )
    }

    @Test
    fun `production Drive sources contain no write API calls or write HTTP methods`() {
        val sourceRoot = listOf(
            File("src/main"),
            File("feature/drive/src/main"),
        ).firstOrNull { it.isDirectory }
            ?: error("Cannot locate feature/drive/src/main for read-only structural verification")

        val sources = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("Expected production Kotlin sources under ${sourceRoot.path}", sources.isNotEmpty())

        val forbiddenPatterns = listOf(
            "HttpURLConnection write method" to Regex(
                """requestMethod\s*=\s*[\"'](?:POST|PUT|PATCH|DELETE)[\"']""",
                RegexOption.IGNORE_CASE,
            ),
            "setRequestMethod write method" to Regex(
                """setRequestMethod\s*\(\s*[\"'](?:POST|PUT|PATCH|DELETE)[\"']""",
                RegexOption.IGNORE_CASE,
            ),
            "Retrofit write annotation" to Regex(
                """@(POST|PUT|PATCH|DELETE)\b""",
                RegexOption.IGNORE_CASE,
            ),
            "Google Drive files write method" to Regex(
                """files\s*(?:\(\))?\s*\.\s*(?:create|update|delete)\s*\(""",
                RegexOption.IGNORE_CASE,
            ),
            "HTTP client write call" to Regex(
                """\.\s*(?:post|put|patch|delete)\s*\(""",
                RegexOption.IGNORE_CASE,
            ),
            "request body output" to Regex(
                """(?:doOutput\s*=\s*true|setDoOutput\s*\(\s*true\s*\))""",
                RegexOption.IGNORE_CASE,
            ),
            "Drive upload endpoint" to Regex(
                """/upload/drive/""",
                RegexOption.IGNORE_CASE,
            ),
        )

        val violations = mutableListOf<String>()
        sources.forEach { source ->
            val text = source.readText()
            forbiddenPatterns.forEach { (label, pattern) ->
                if (pattern.containsMatchIn(text)) {
                    violations += "$label in ${source.relativeTo(sourceRoot).path}"
                }
            }
        }

        assertTrue(
            "Google Drive production code must remain read-only by implementation; violations: $violations",
            violations.isEmpty(),
        )
    }
}
