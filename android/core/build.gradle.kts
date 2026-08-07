// Plain Kotlin/JVM module: no Android Gradle Plugin, no android.*/androidx.* imports.
// This is what lets `domain` and `data` build and unit-test with just a JDK, on either
// the main or secondary PC -- see docs/phase3-android-app-design.md#gradle-module-layout-addendum-phase-3b-implementation-start
plugins {
    alias(libs.plugins.kotlin.jvm)
    // java-library (not plain java, which org.jetbrains.kotlin.jvm applies by default) is
    // what provides the `api` configuration below -- needed so :app can see Retrofit/OkHttp/
    // kotlinx.serialization types directly, not just kotlinx-coroutines' suspend-fun usage.
    id("java-library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    // Targets bytecode 17 using whichever JDK Gradle itself is running on -- no toolchain
    // provisioning/auto-download required, so this builds on either PC's already-installed JDK.
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    // `api`, not `implementation`, for anything :app's own code references directly (Hilt
    // DI wiring in :app's NetworkModule constructs Retrofit/OkHttpClient/Json itself, and
    // :app's NetworkModule constructs OkHttpClient directly) -- `implementation`
    // would hide these types from :app's compile classpath entirely, which is exactly what
    // broke the first Android-SDK build of this module (see git history for this file).
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit)
    api(libs.retrofit.serialization.converter)
    api(libs.okhttp)
    api(libs.javax.inject)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
