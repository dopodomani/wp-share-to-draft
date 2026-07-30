package io.github.dopodomani.wpsharetodraft.domain

/**
 * Thin logging port so [io.github.dopodomani.wpsharetodraft.data.WordPressDestination] can log
 * which transport it used without pulling `android.util.Log` into this SDK-independent module.
 * See docs/phase3c-android-xmlrpc-design.md#logger-port-new-small.
 */
interface Logger {
    fun d(
        tag: String,
        message: String,
    )
}
