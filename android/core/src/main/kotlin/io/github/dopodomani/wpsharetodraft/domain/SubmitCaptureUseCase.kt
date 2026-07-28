package io.github.dopodomani.wpsharetodraft.domain

import javax.inject.Inject

/**
 * Thin orchestration: hands a [CaptureItem] to the configured [Destination] unchanged and
 * returns its result as-is -- no swallowed/re-wrapped errors.
 */
class SubmitCaptureUseCase
    @Inject
    constructor(private val destination: Destination) {
        suspend fun submit(item: CaptureItem): Result<DraftResult> = destination.send(item)
    }
