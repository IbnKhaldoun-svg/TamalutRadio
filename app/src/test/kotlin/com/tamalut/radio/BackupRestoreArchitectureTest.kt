package com.tamalut.radio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreArchitectureTest {
    @Test
    fun settingsUsesSafAndRestoreDoesNotOwnPlayback() {
        val mainActivity = Path.of("src/main/kotlin/com/tamalut/radio/MainActivity.kt").readText()
        val settings = Path.of("src/main/kotlin/com/tamalut/radio/BackupRestoreSettings.kt").readText()
        val coordinator = Path.of("src/main/kotlin/com/tamalut/radio/BackupRestore.kt").readText()
        val manifest = Path.of("src/main/AndroidManifest.xml").readText()

        assertTrue(mainActivity.contains("BackupRestoreSettings("))
        assertTrue(settings.contains("ActivityResultContracts.CreateDocument(\"application/json\")"))
        assertTrue(settings.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(settings.contains("MAX_BACKUP_BYTES"))
        assertTrue(settings.contains("I dati gestiti dal backup sostituiranno"))
        assertTrue(settings.contains("JSON leggibile e non cifrato"))
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))

        assertFalse(coordinator.contains("Media3PlaybackController"))
        assertFalse(coordinator.contains("TamalutPlaybackService"))
        assertFalse(coordinator.contains("MediaLibrarySession"))
        assertFalse(coordinator.contains("ExoPlayer"))
        assertFalse(coordinator.contains("SleepTimer"))
        assertFalse(coordinator.contains("localFolderUri"))
        assertFalse(coordinator.contains("lastPlayed"))
    }

    @Test
    fun restorePreflightUsesCurrentCatalogWithoutSeedOrNetworkProbe() {
        val coordinator = Path.of("src/main/kotlin/com/tamalut/radio/BackupRestore.kt").readText()
        val prepareImport = coordinator.substringAfter("suspend fun prepareImport")
            .substringBefore("suspend fun restore")

        assertTrue(coordinator.contains("InitialRadioCatalog.stations"))
        assertTrue(prepareImport.contains("normalizeHttpsStreamUrl"))
        assertFalse(prepareImport.contains("seedInitialCatalog"))
        assertFalse(prepareImport.contains("replaceBackupManagedData"))
        assertFalse(coordinator.contains("HttpsRadioStreamValidator"))
        assertFalse(coordinator.contains("RadioHttpProbe"))
        assertFalse(coordinator.contains("UrlConnectionRadioHttpProbe"))
    }
}
