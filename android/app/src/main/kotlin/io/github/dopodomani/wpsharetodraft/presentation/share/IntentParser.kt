package io.github.dopodomani.wpsharetodraft.presentation.share

import android.content.Intent
import android.util.Patterns
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.TitleMode
import java.time.Clock
import javax.inject.Inject

private const val SOURCE = "chrome_share"

/**
 * Chrome prefixes some shared text with a template line built around the source URL --
 * observed variants include a bare "リンク："/"Link:" label and "リンク: を含む" ("...including
 * the link:") -- but when Chrome can't determine a URL to fill in, the template renders with
 * that gap empty, leaving only its own boilerplate words. Confirmed on-device: this leftover
 * template text, not the actual selected text on the next line, was being picked up as the
 * title. Rather than matching one exact template string (fragile against variants/locales),
 * a candidate line is treated as noise if stripping every known template word from it leaves
 * nothing -- a line with any real content alongside these words is left untouched.
 */
private val LINK_TEMPLATE_LABEL = Regex("^\\s*(リンク|Link)\\s*[:：]\\s*(を含む|including)?\\s*$", RegexOption.IGNORE_CASE)

private fun isLinkTemplateNoise(line: String): Boolean = LINK_TEMPLATE_LABEL.matches(line.normalizeForLineCheck())

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
        fun parse(intent: Intent, titleMode: TitleMode = TitleMode.AUTO): CaptureItem {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

            val url = findUrl(sharedText)
            val remainder = sharedText?.let { removeUrl(it, url) }

            val subjectTitle = subject
                ?.let { removeUrl(it, url) }
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !isLinkTemplateNoise(it) }
            val firstLineTitle = firstLine(remainder)
            val title = when (titleMode) {
                TitleMode.AUTO -> subjectTitle ?: firstLineTitle ?: ""
                TitleMode.FIRST_LINE -> firstLineTitle ?: subjectTitle ?: ""
            }

            val remainderText = remainder?.takeIf { it.isNotBlank() }
            val memoText = when {
                titleMode != TitleMode.FIRST_LINE -> remainderText
                firstLineTitle != null -> removeFirstMeaningfulLine(remainder)
                else -> null
            }

            return CaptureItem(
                title = title,
                url = url ?: "",
                sharedText = remainderText,
                // Pre-fills the visible メモ field with the same text so sharing a Chrome text
                // selection (which often carries no URL/title at all -- see
                // docs/phase3-android-app-design.md's IntentParser revision 2) doesn't look
                // like nothing was captured. sharedText keeps the identical value for its own,
                // separate role (raw captured text sent to WordPress as shared_text).
                memo = memoText,
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
                ?.firstOrNull { it.isEffectivelyNonBlank() && !isLinkTemplateNoise(it) }

        private fun removeFirstMeaningfulLine(text: String?): String? {
            if (text == null) return null
            val lines = text.split(Regex("\\r\\n|\\r|\\n"))
            var index = 0
            while (index < lines.size && (!lines[index].isEffectivelyNonBlank() || isLinkTemplateNoise(lines[index]))) index++
            if (index >= lines.size) return null
            return lines.drop(index + 1).joinToString("\n").trim().takeIf { it.isNotEmpty() }
        }
    }

private fun String.isEffectivelyNonBlank(): Boolean =
    normalizeForLineCheck().trim().isNotEmpty()

private fun String.normalizeForLineCheck(): String =
    replace(Regex("[\\u00A0\\u3000]"), " ")
        .replace(Regex("""\p{Cf}"""), "")
        .replace("\u200B", "")
        .replace("\uFEFF", "")
