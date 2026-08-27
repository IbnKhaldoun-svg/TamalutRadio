plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

android {
    namespace = "com.tamalut.radio.core.database"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation("androidx.room3:room3-runtime:3.0.2")
    ksp("androidx.room3:room3-compiler:3.0.2")

    testImplementation("junit:junit:4.13.2")
}
