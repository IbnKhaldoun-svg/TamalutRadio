plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val isGithubActions = System.getenv("GITHUB_ACTIONS") == "true"
val debugKeystorePath = System.getenv("TAMALUT_DEBUG_KEYSTORE_PATH")
val debugKeystorePassword = System.getenv("TAMALUT_DEBUG_KEYSTORE_PASSWORD")
val debugKeyAlias = System.getenv("TAMALUT_DEBUG_KEY_ALIAS")
val debugKeyPassword = System.getenv("TAMALUT_DEBUG_KEY_PASSWORD")
val debugSigningValues = listOf(
    debugKeystorePath,
    debugKeystorePassword,
    debugKeyAlias,
    debugKeyPassword,
)
val persistentDebugSigningEnabled = debugSigningValues.all { !it.isNullOrBlank() }

if (debugSigningValues.any { !it.isNullOrBlank() } && !persistentDebugSigningEnabled) {
    error("Persistent debug signing configuration is incomplete")
}
if (isGithubActions && !persistentDebugSigningEnabled) {
    error("GitHub Actions debug builds require the persistent TamalutRadio debug signing key")
}

android {
    namespace = "com.tamalut.radio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tamalut.radio"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (persistentDebugSigningEnabled) {
            create("persistentDebug") {
                storeFile = file(requireNotNull(debugKeystorePath))
                storePassword = requireNotNull(debugKeystorePassword)
                keyAlias = requireNotNull(debugKeyAlias)
                keyPassword = requireNotNull(debugKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (persistentDebugSigningEnabled) {
                signingConfig = signingConfigs.getByName("persistentDebug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:preferences"))
    implementation(project(":core:playback"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":feature:radio"))
    implementation(project(":feature:library"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.room3:room3-runtime:3.0.2")
    implementation("androidx.sqlite:sqlite-framework:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
