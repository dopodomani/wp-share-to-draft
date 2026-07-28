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

rootProject.name = "wp-share-to-draft"

// :core is a plain Kotlin/JVM module (no Android Gradle Plugin) so it builds and tests
// with just a JDK -- see docs/phase3-android-app-design.md#gradle-module-layout-addendum-phase-3b-implementation-start
include(":core")

// :app is the Android application module (Compose, Hilt, IntentParser, EncryptedSettingsRepository)
// and requires the Android SDK to build -- not buildable in an SDK-less environment.
include(":app")
