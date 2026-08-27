package com.tamalut.radio.feature.library

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.tamalut.radio.core.model.MediaId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LocalAudioScanner {
    suspend fun scan(treeUri: String): List<LocalAudioTrack>
}

interface LocalFolderAccess {
    fun persistReadPermission(treeUri: String)
}

class SafFolderAccess(
    private val contentResolver: ContentResolver,
) : LocalFolderAccess {
    override fun persistReadPermission(treeUri: String) {
        contentResolver.takePersistableUriPermission(
            Uri.parse(treeUri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

class SafLocalAudioScanner(
    private val contentResolver: ContentResolver,
) : LocalAudioScanner {
    override suspend fun scan(treeUri: String): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        val parsedTreeUri = Uri.parse(treeUri)
        val rootDocumentId = DocumentsContract.getTreeDocumentId(parsedTreeUri)
        val reader = ContentResolverDocumentTreeReader(contentResolver)
        collectAudioTracks(
            rootDocumentId = rootDocumentId,
            childrenProvider = { parentDocumentId ->
                reader.children(parsedTreeUri, parentDocumentId)
            },
        )
    }
}

internal data class DocumentEntry(
    val documentId: String,
    val displayName: String?,
    val mimeType: String?,
    val contentUri: String,
    val isDirectory: Boolean,
)

internal fun collectAudioTracks(
    rootDocumentId: String,
    childrenProvider: (String) -> List<DocumentEntry>,
): List<LocalAudioTrack> {
    val result = mutableListOf<LocalAudioTrack>()

    fun visit(parentDocumentId: String, isRoot: Boolean) {
        val children = if (isRoot) {
            childrenProvider(parentDocumentId)
        } else {
            try {
                childrenProvider(parentDocumentId)
            } catch (_: SecurityException) {
                return
            } catch (_: IllegalArgumentException) {
                return
            }
        }

        children.forEach { child ->
            if (child.isDirectory) {
                visit(child.documentId, isRoot = false)
            } else if (isAudioDocument(child.displayName, child.mimeType)) {
                val displayName = child.displayName?.trim().orEmpty()
                val title = displayName
                    .substringBeforeLast('.', missingDelimiterValue = displayName)
                    .trim()
                    .ifEmpty { "Audio" }
                result += LocalAudioTrack(
                    id = MediaId(child.contentUri),
                    title = title,
                    contentUri = child.contentUri,
                    mimeType = child.mimeType,
                )
            }
        }
    }

    visit(rootDocumentId, isRoot = true)
    return result.sortedWith(
        compareBy<LocalAudioTrack> { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.contentUri },
    )
}

internal fun isAudioDocument(
    displayName: String?,
    mimeType: String?,
): Boolean {
    if (mimeType?.startsWith("audio/", ignoreCase = true) == true) {
        return true
    }

    val genericMime = mimeType == null ||
        mimeType.equals("application/octet-stream", ignoreCase = true) ||
        mimeType.equals("application/ogg", ignoreCase = true)
    if (!genericMime) {
        return false
    }

    val extension = displayName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.ROOT)
        ?: return false
    return extension in AUDIO_EXTENSIONS
}

private val AUDIO_EXTENSIONS = setOf(
    "mp3",
    "m4a",
    "aac",
    "flac",
    "ogg",
    "oga",
    "opus",
    "wav",
    "mka",
    "amr",
)

private class ContentResolverDocumentTreeReader(
    private val contentResolver: ContentResolver,
) {
    fun children(treeUri: Uri, parentDocumentId: String): List<DocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId,
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        val entries = mutableListOf<DocumentEntry>()
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val documentIdColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            )
            val displayNameColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(documentIdColumn)
                val displayName = if (cursor.isNull(displayNameColumn)) {
                    null
                } else {
                    cursor.getString(displayNameColumn)
                }
                val mimeType = if (cursor.isNull(mimeTypeColumn)) {
                    null
                } else {
                    cursor.getString(mimeTypeColumn)
                }
                entries += DocumentEntry(
                    documentId = documentId,
                    displayName = displayName,
                    mimeType = mimeType,
                    contentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        documentId,
                    ).toString(),
                    isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                )
            }
        }
        return entries
    }
}
