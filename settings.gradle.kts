import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TamalutRadio"
include(":app")
include(":core:designsystem")
include(":core:model")
include(":core:cloud")
include(":core:preferences")
include(":core:database")
include(":core:data")
include(":core:playback")
include(":feature:radio")
include(":feature:library")
include(":feature:drive")
