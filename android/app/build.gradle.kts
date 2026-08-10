// Android application module: Compose, Hilt, IntentParser, EncryptedSettingsRepository.
// Requires the Android SDK to build/test -- see
// docs/phase3-android-app-design.md#gradle-module-layout-addendum-phase-3b-implementation-start
// NOT buildable in an environment without the Android SDK installed (compileSdk below);
// see docs/development.md for which PC/CI this runs on.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.github.dopodomani.wpsharetodraft"
    // Bumped from 35: androidx.core 1.19.0/lifecycle 2.11.0/hilt-navigation-compose 1.4.0
    // (all pulled in by this dependency round) require compiling against API 37 or later.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.dopodomani.wpsharetodraft"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.security.crypto)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Robolectric's stable support is JUnit4-based (@RunWith(RobolectricTestRunner::class));
    // junit-vintage-engine lets the JUnit5 platform run those alongside the JUnit5 tests
    // elsewhere in this module.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
