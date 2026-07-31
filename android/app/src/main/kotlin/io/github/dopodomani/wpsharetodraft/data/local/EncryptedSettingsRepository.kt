package io.github.dopodomani.wpsharetodraft.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import javax.inject.Inject

private const val KEY_SITE_URL = "site_url"
private const val KEY_USERNAME = "username"
private const val KEY_APPLICATION_PASSWORD = "application_password"
private const val KEY_CONNECTION_METHOD = "connection_method"

/**
 * Reads/writes site_url, username, application_password keys in the injected (already
 * Keystore-backed) [SharedPreferences]. Never logged -- see
 * docs/security.md#credential-storage-android -- no Log.* call anywhere in this class
 * touches any of these values.
 */
class EncryptedSettingsRepository
    @Inject
    constructor(private val encryptedPrefs: SharedPreferences) : SettingsRepository {
        override suspend fun hasSettings(): Boolean = encryptedPrefs.contains(KEY_SITE_URL)

        override suspend fun get(): AppSettings? {
            val siteUrl = encryptedPrefs.getString(KEY_SITE_URL, null) ?: return null
            val username = encryptedPrefs.getString(KEY_USERNAME, null) ?: return null
            val applicationPassword = encryptedPrefs.getString(KEY_APPLICATION_PASSWORD, null) ?: return null
            // Absent for settings saved before this field existed -- defaults to XML_RPC,
            // same as a fresh AppSettings(), rather than failing to parse.
            val connectionMethod =
                encryptedPrefs.getString(KEY_CONNECTION_METHOD, null)
                    ?.let { runCatching { ConnectionMethod.valueOf(it) }.getOrNull() }
                    ?: ConnectionMethod.XML_RPC
            return AppSettings(siteUrl, username, applicationPassword, connectionMethod)
        }

        override suspend fun save(settings: AppSettings) {
            encryptedPrefs.edit {
                putString(KEY_SITE_URL, settings.siteUrl)
                putString(KEY_USERNAME, settings.username)
                putString(KEY_APPLICATION_PASSWORD, settings.applicationPassword)
                putString(KEY_CONNECTION_METHOD, settings.connectionMethod.name)
            }
        }
    }
