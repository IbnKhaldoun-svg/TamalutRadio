package com.tamalut.radio

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Test

class DriveRemovalArchitectureTest {
    @Test
    fun `active app and Gradle graph contain no Google Drive integration`() {
        val mainActivity = Path.of("src/main/kotlin/com/tamalut/radio/MainActivity.kt").readText()
        val appGradle = Path.of("build.gradle.kts").readText()
        val settingsGradle = Path.of("../settings.gradle.kts").readText()

        assertFalse(mainActivity.contains("DriveFolderProbeCard"))
        assertFalse(mainActivity.contains("DriveMultiFilePickerCard"))
        assertFalse(appGradle.contains(":feature:drive"))
        assertFalse(appGradle.contains("play-services-auth"))
        assertFalse(settingsGradle.contains(":feature:drive"))
        assertFalse(Files.exists(Path.of("../feature/drive")))
    }
}
