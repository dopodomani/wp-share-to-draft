package io.github.dopodomani.wpsharetodraft.data.local

import io.github.dopodomani.wpsharetodraft.domain.Logger
import javax.inject.Inject

/** Real [Logger] implementation -- the only place `android.util.Log` is used for this port. */
class AndroidLogger
    @Inject
    constructor() : Logger {
        override fun d(
            tag: String,
            message: String,
        ) {
            android.util.Log.d(tag, message)
        }
    }
