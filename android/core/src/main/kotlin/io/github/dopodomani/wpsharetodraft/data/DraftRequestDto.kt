package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire shape for `POST /draft`, mirroring docs/api-spec.md exactly. */
@Serializable
data class DraftRequestDto(
    val title: String,
    val url: String,
    @SerialName("shared_text") val sharedText: String?,
    val memo: String?,
    val source: String,
    @SerialName("shared_at") val sharedAt: String?,
) {
    companion object {
        fun from(item: CaptureItem): DraftRequestDto =
            DraftRequestDto(
                title = item.title,
                url = item.url,
                sharedText = item.sharedText,
                memo = item.memo,
                source = item.source,
                // Instant.toString() is ISO-8601 with a "Z" suffix, satisfying the plugin's
                // required shared_at format (offset or Z) -- see docs/api-spec.md.
                sharedAt = item.sharedAt.toString(),
            )
    }
}
