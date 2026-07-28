package io.github.dopodomani.wpsharetodraft.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

class SubmitCaptureUseCaseTest {
    private val item =
        CaptureItem(
            title = "Title",
            url = "https://example.com",
            sharedText = null,
            memo = null,
            source = "chrome_share",
            sharedAt = Instant.parse("2026-07-28T09:15:00Z"),
        )

    @Test
    fun `submit passes the item to the destination unchanged`() =
        runTest {
            var received: CaptureItem? = null
            val destination =
                Destination { captured ->
                    received = captured
                    Result.success(
                        DraftResult(1, "draft", "[INBOX] Title", null, null, "素材候補", Instant.now()),
                    )
                }

            SubmitCaptureUseCase(destination).submit(item)

            assertEquals(item, received)
        }

    @Test
    fun `a destination failure propagates as-is`() =
        runTest {
            val failure = CaptureError.CategoryUnavailable.asThrowable()
            val destination = Destination { Result.failure(failure) }

            val result = SubmitCaptureUseCase(destination).submit(item)

            assertSame(failure, result.exceptionOrNull())
        }

    /** SAM-style fake -- the interface is small enough that this is clearer than a mocking DSL. */
    private fun Destination(send: suspend (CaptureItem) -> Result<DraftResult>): Destination =
        object : Destination {
            override suspend fun send(item: CaptureItem): Result<DraftResult> = send.invoke(item)
        }
}
