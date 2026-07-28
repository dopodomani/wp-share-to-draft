package io.github.dopodomani.wpsharetodraft.domain

import java.time.Instant

/**
 * The central domain model this app is built around (see
 * docs/phase3-android-app-design.md#captureitem--the-central-domain-model). Every layer
 * converges on this shape: `IntentParser` (in `:app`) produces it, the Confirm screen edits
 * it, [SubmitCaptureUseCase] and [Destination] both take it as their sole input, and it's
 * the only shape `domain` needs to know about.
 */
data class CaptureItem(
    val title: String,
    val url: String,
    val sharedText: String?,
    val memo: String?,
    val source: String,
    val sharedAt: Instant,
)
