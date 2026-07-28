package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.DraftResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Wire shape for a successful `201` response, mirroring docs/api-spec.md exactly. */
@Serializable
data class DraftResponseDto(
    @SerialName("post_id") val postId: Long,
    val status: String,
    val title: String,
    @SerialName("edit_url") val editUrl: String?,
    @SerialName("preview_url") val previewUrl: String?,
    val category: String,
    @SerialName("created_at") val createdAt: String,
) {
    fun toDomain(): DraftResult =
        DraftResult(
            postId = postId,
            status = status,
            title = title,
            editUrl = editUrl,
            previewUrl = previewUrl,
            category = category,
            createdAt = Instant.parse(createdAt),
        )
}

/** Wire shape for this plugin's own error bodies -- see docs/api-spec.md's error responses. */
@Serializable
data class DraftErrorDto(
    val code: String,
    val message: String,
)
