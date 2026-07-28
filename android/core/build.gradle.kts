// Plain Kotlin/JVM module: no Android Gradle Plugin, no android.*/androidx.* imports.
// This is what lets `domain` and `data` build and unit-test with just a JDK, on either
// the main or secondary PC -- see docs/phase3-android-app-design.md#gradle-module-layout-addendum-phase-3b-implementation-start
plugins {
    id("org.jetbrains.kotlin.jvm")
    // java-library (not plain java, which org.jetbrains.kotlin.jvm applies by default) is
    // what provides the `api` configuration below -- needed so :app can see Retrofit/OkHttp/
    // kotlinx.serialization types directly, not just kotlinx-coroutines' suspend-fun usage.
    id("java-library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
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
    // :app's AuthInterceptor usage needs okhttp3.Interceptor visible) -- `implementation`
    // would hide these types from :app's compile classpath entirely, which is exactly what
    // broke the first Android-SDK build of this module (see git history for this file).
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("com.squareup.retrofit2:retrofit:2.11.0")
    api("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("javax.inject:javax.inject:1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
