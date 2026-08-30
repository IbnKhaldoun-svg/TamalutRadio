plugins {
    id("com.android.library")
}

android {
    namespace = "com.tamalut.radio.feature.drive"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:cloud"))
    api("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation(project(":core:model"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
