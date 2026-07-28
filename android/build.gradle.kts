// Root build file. Plugin versions are declared here (applied `false`) and applied
// per-module, per the standard Gradle Kotlin DSL convention.
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.android.application") version "8.5.2" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
}
