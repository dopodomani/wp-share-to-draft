package io.github.dopodomani.wpsharetodraft.presentation.share

import android.content.Intent
import android.util.Patterns
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import java.time.Clock
import javax.inject.Inject

private const val SOURCE = "chrome_share"

/**
 * Translates a raw Android [Intent] (Chrome's `ACTION_SEND`) into a [CaptureItem]. The one
 * piece of `presentation` that touches raw Intent extras -- isolated here, rather than
 * inline in [ShareReceiverActivity], specifically so the URL-extraction heuristic is
 * unit-testable without instantiating an Activity. See
 * docs/phase3-android-app-design.md#intentparser--independent-unit-testable-intent--captureitem-translation.
 *
 * Never throws for "couldn't find a URL" -- that's an expected, user-correctable case; the
 * Confirm screen opens with an empty, editable `url` field instead.
 */
class IntentParser
    @Inject
    constructor(private val clock: Clock) {
        fun parse(intent: Intent): CaptureItem {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

            val url = findUrl(sharedText)
            val remainder = sharedText?.let { removeUrl(it, url) }

            val title = subject?.takeIf { it.isNotBlank() } ?: firstLine(sharedText) ?: ""

            return CaptureItem(
                title = title,
                url = url ?: "",
                sharedText = remainder?.takeIf { it.isNotBlank() },
                memo = null,
                source = SOURCE,
                sharedAt = clock.instant(),
            )
        }

        private fun findUrl(text: String?): String? {
            if (text.isNullOrBlank()) return null
            val matcher = Patterns.WEB_URL.matcher(text)
            return if (matcher.find()) text.substring(matcher.start(), matcher.end()) else null
        }

        private fun removeUrl(
            text: String,
            url: String?,
        ): String? = if (url == null) text else text.replace(url, "").trim()

        private fun firstLine(text: String?): String? = text?.lineSequence()?.firstOrNull { it.isNotBlank() }
    }
