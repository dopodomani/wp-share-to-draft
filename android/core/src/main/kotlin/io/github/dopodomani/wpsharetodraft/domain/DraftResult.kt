package io.github.dopodomani.wpsharetodraft.domain

import java.time.Instant

/** The outcome of a successful [Destination.send], mirroring the `201` body in docs/api-spec.md. */
data class DraftResult(
    val postId: Long,
    val status: String,
    val title: String,
    val editUrl: String?,
    val previewUrl: String?,
    val category: String,
    val createdAt: Instant,
)
