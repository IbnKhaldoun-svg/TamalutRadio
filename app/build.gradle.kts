plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.room3:room3-runtime:3.0.2")
    implementation("androidx.sqlite:sqlite-framework:2.7.0")
}
