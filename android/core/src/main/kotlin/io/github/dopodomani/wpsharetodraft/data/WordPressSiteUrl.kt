package io.github.dopodomani.wpsharetodraft.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Validates and canonicalizes the root URL of a WordPress installation. */
object WordPressSiteUrl {
    fun normalize(value: String): String? {
        val url = value.trim().toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.host.isBlank()) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null

        return url.toString().trimEnd('/')
    }
}
