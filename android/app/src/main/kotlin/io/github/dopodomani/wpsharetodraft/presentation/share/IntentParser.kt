package io.github.dopodomani.wpsharetodraft.presentation.share

import android.content.Intent
import android.util.Patterns
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import java.time.Clock
import javax.inject.Inject

private const val SOURCE = "chrome_share"

/**
 * Chrome prefixes some shared text with a "リンク：" (or "Link:") label line, meant to be
 * followed by the source URL -- but when Chrome can't determine one (see
 * docs/phase3-android-app-design.md's IntentParser revision 2), the label is left dangling
 * with nothing after it. Confirmed on-device: this label line, not the actual selected text,
 * was being picked up as the title. Matched whether or not a URL still follows it (it won't,
 * by the time this runs, since [IntentParser] already strips any detected URL first).
 */
private val LINK_LABEL_ONLY_LINE = Regex("^(リンク|Link)\\s*[:：]?\\s*$", RegexOption.IGNORE_CASE)

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

            val title = subject?.takeIf { it.isNotBlank() } ?: firstLine(remainder) ?: ""

            val remainderText = remainder?.takeIf { it.isNotBlank() }

            return CaptureItem(
                title = title,
                url = url ?: "",
                sharedText = remainderText,
                // Pre-fills the visible メモ field with the same text so sharing a Chrome text
                // selection (which often carries no URL/title at all -- see
                // docs/phase3-android-app-design.md's IntentParser revision 2) doesn't look
                // like nothing was captured. sharedText keeps the identical value for its own,
                // separate role (raw captured text sent to WordPress as shared_text).
                memo = remainderText,
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

        private fun firstLine(text: String?): String? =
            text
                ?.lineSequence()
                ?.map { it.trim() }
                ?.firstOrNull { it.isNotBlank() && !LINK_LABEL_ONLY_LINE.matches(it) }
    }
