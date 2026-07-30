package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.DraftResult

/**
 * One level below [io.github.dopodomani.wpsharetodraft.domain.Destination]: `Destination` is
 * the app-wide "where does this go" port (WordPress today, GitHub/Notion/Slack future);
 * `WordPressPublisher` is specifically "how do I talk to WordPress," an implementation detail
 * of the one WordPress `Destination`. Never bound to `Destination` directly and never called
 * from `presentation` -- see docs/phase3c-android-xmlrpc-design.md.
 */
interface WordPressPublisher {
    suspend fun publish(
        item: CaptureItem,
        settings: AppSettings,
    ): Result<DraftResult>
}
