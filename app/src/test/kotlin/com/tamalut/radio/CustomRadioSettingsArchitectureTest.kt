package com.tamalut.radio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRadioSettingsArchitectureTest {
    @Test
    fun managementLivesOnlyInSettingsAndRoomMigrationIsRegistered() {
        val mainActivity = Path.of("src/main/kotlin/com/tamalut/radio/MainActivity.kt").readText()
        val radioScreen = Path.of(
            "../feature/radio/src/main/kotlin/com/tamalut/radio/feature/radio/RadioScreen.kt",
        ).readText()
        val settingsUi = Path.of(
            "../feature/radio/src/main/kotlin/com/tamalut/radio/feature/radio/RadioManagementSettings.kt",
        ).readText()
        val database = Path.of(
            "../core/database/src/main/kotlin/com/tamalut/radio/core/database/TamalutDatabaseMigrations.kt",
        ).readText()

        assertTrue(mainActivity.contains(".addMigrations(MIGRATION_1_2)"))
        assertTrue(mainActivity.contains("RadioManagementSettings(viewModel = radioViewModel)"))
        assertTrue(settingsUi.contains("\"Aggiungi radio\""))
        assertTrue(settingsUi.contains("\"Modifica radio\""))
        assertTrue(settingsUi.contains("isCustomEditPickerOpen"))
        assertTrue(settingsUi.contains("+ Nuova categoria…"))
        assertTrue(settingsUi.contains("Elimina radio"))

        assertFalse(radioScreen.contains("Icons.Filled.Add"))
        assertFalse(radioScreen.contains("CustomStationMenu"))
        assertFalse(radioScreen.contains("RadioStationFilter.PERSONAL"))
        assertFalse(radioScreen.contains("Personali"))
        assertFalse(radioScreen.contains("onAddCustomStation"))
        assertTrue(radioScreen.contains("filters = state.availableFilters"))

        assertTrue(database.contains("Migration(1, 2)"))
        assertTrue(database.contains("custom_category"))
        assertTrue(database.contains("Altro"))
    }
}
