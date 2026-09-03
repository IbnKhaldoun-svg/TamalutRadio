package com.tamalut.radio.core.database

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreDaoContractTest {
    @Test
    fun backupManagedReplacementIsOneExplicitRoomTransaction() {
        val source = Path.of(
            "src/main/kotlin/com/tamalut/radio/core/database/Daos.kt",
        ).readText()
        val dao = source.substringAfter("interface BackupRestoreDao")
            .substringBefore("@Dao\ninterface RecentlyPlayedDao")

        assertTrue(dao.contains("@Transaction"))
        assertTrue(dao.contains("suspend fun replaceBackupManagedData"))
        val body = dao.substringAfter("suspend fun replaceBackupManagedData")
        val deleteFavorites = body.indexOf("deleteAllFavorites()")
        val deleteCustom = body.indexOf("deleteAllCustomStations()")
        val upsertStations = body.indexOf("upsertStations(customStations)")
        val upsertFavorites = body.indexOf("upsertFavorites(favorites)")
        assertTrue(deleteFavorites >= 0)
        assertTrue(deleteCustom >= 0)
        assertTrue(upsertStations >= 0)
        assertTrue(upsertFavorites >= 0)
        assertTrue(deleteFavorites < deleteCustom)
        assertTrue(deleteCustom < upsertStations)
        assertTrue(upsertStations < upsertFavorites)
    }

    @Test
    fun backupDaoReadsOnlyCustomRowsAndFavoritesBeforeRestore() {
        val source = Path.of(
            "src/main/kotlin/com/tamalut/radio/core/database/Daos.kt",
        ).readText()
        val dao = source.substringAfter("interface BackupRestoreDao")
            .substringBefore("@Dao\ninterface RecentlyPlayedDao")

        assertTrue(dao.contains("SELECT * FROM radio_stations WHERE is_custom = 1"))
        assertFalse(dao.contains("SELECT * FROM radio_stations WHERE is_custom = 0"))
        assertTrue(dao.contains("DELETE FROM radio_stations WHERE is_custom = 1"))
        assertTrue(dao.contains("SELECT * FROM favorite_stations"))
    }
}
