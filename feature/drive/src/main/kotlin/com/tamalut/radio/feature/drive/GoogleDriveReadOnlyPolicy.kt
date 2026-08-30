package com.tamalut.radio.feature.drive

import java.net.URI

enum class GoogleDriveReadOperation {
    FILES_LIST,
    FILES_GET,
}

/**
 * TamalutRadio deliberately treats the Drive grant as read-only at the implementation
 * boundary even though drive.file is technically a read/write OAuth scope.
 *
 * Only Drive v3 files.list and files.get are admitted, both over HTTPS GET.
 */
object GoogleDriveReadOnlyPolicy {
    val allowedHttpMethods: Set<String> = setOf("GET")
    val allowedOperations: Set<GoogleDriveReadOperation> = setOf(
        GoogleDriveReadOperation.FILES_LIST,
        GoogleDriveReadOperation.FILES_GET,
    )

    internal fun requireAllowedGet(url: String): GoogleDriveReadOperation {
        val uri = URI(url)
        require(uri.scheme == "https") { "Google Drive reads must use HTTPS" }
        require(uri.host == "www.googleapis.com") { "Unexpected Google Drive API host" }

        val segments = uri.path.trim('/').split('/').filter(String::isNotBlank)
        val operation = when {
            segments == listOf("drive", "v3", "files") -> GoogleDriveReadOperation.FILES_LIST
            segments.size == 4 && segments.take(3) == listOf("drive", "v3", "files") -> {
                GoogleDriveReadOperation.FILES_GET
            }
            else -> error("Drive endpoint is outside the read-only files.list/files.get boundary")
        }
        check(operation in allowedOperations)
        return operation
    }
}
