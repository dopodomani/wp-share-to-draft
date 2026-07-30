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
    val connectionMethod: ConnectionMethod = ConnectionMethod.XML_RPC,
)

/**
 * Which transport [io.github.dopodomani.wpsharetodraft.data.WordPressDestination] uses to
 * talk to WordPress. `XML_RPC` is the default -- see
 * docs/tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport for why a fresh install
 * defaults to the transport confirmed to work, not REST. User-selected, never auto-switched.
 */
enum class ConnectionMethod { XML_RPC, REST }
