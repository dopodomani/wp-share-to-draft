package io.github.dopodomani.wpsharetodraft.domain

/**
 * Local (on-device) configuration -- named for the responsibility it manages ("this app's
 * configuration"), not just the secret it happens to also hold. See
 * docs/phase3-android-app-design.md#4-repository-construction for why this replaced a
 * `CredentialRepository` in an earlier revision.
 */
interface SettingsRepository {
    suspend fun hasSettings(): Boolean

    /** Null if never configured. */
    suspend fun get(): AppSettings?

    suspend fun save(settings: AppSettings)
}

data class AppSettings(
    val siteUrl: String,
    val username: String,
    val applicationPassword: String,
)
