package io.github.dopodomani.wpsharetodraft.domain

/**
 * Port for "where does a [CaptureItem] get sent." Named `Destination`, not `Repository` --
 * see docs/tech-decisions.md#5-clean-architecture--explicit-destination-interface -- so that
 * adding GitHub/Notion/Slack/Webhook sinks later is a new implementation, not a rename.
 */
interface Destination {
    suspend fun send(item: CaptureItem): Result<DraftResult>
}
