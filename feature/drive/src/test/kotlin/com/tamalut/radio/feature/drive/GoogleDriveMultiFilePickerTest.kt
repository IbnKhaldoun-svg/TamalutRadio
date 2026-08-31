package com.tamalut.radio.feature.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveMultiFilePickerTest {
    @Test
    fun pickedFileIdsParserKeepsAllDistinctIdsInPickerOrder() {
        assertEquals(
            listOf("file-1", "file-2", "file-3"),
            GoogleDriveAuthorizationResultParser.parsePickedItemIds(
                " file-1, ,file-2,file-1, file-3 ",
            ),
        )
        assertTrue(GoogleDriveAuthorizationResultParser.parsePickedItemIds(" , ").isEmpty())
        assertTrue(GoogleDriveAuthorizationResultParser.parsePickedItemIds(null).isEmpty())
    }

    @Test
    fun compatibilitySingleIdParserUsesFirstSelectedFile() {
        assertEquals(
            "file-1",
            GoogleDriveAuthorizationResultParser.parsePickedItemId(" file-1, file-2 "),
        )
        assertNull(GoogleDriveAuthorizationResultParser.parsePickedItemId(" , "))
        assertNull(GoogleDriveAuthorizationResultParser.parsePickedItemId(null))
    }
}
