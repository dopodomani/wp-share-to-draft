package io.github.dopodomani.wpsharetodraft.presentation.share

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Confirmed in Phase 3a review round 2: dedicated test for the intent-parsing heuristic
 * (docs/phase3-android-app-design.md#intentparser--independent-unit-testable-intent--captureitem-translation),
 * covering every row of its extraction table across realistic Chrome share-intent shapes.
 *
 * Needs Robolectric (Android SDK on the build classpath) since it touches android.content.Intent
 * directly -- the one exception to "the secondary PC can run everything" (see
 * docs/phase3-android-app-design.md#9-android-test-strategy). Not run in this environment;
 * see docs/development.md.
 */
@RunWith(RobolectricTestRunner::class)
class IntentParserTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-07-28T09:15:00Z"), ZoneOffset.UTC)
    private val parser = IntentParser(fixedClock)

    @Test
    fun `subject and a URL in the shared text are both extracted`() {
        val intent =
            sendIntent(
                subject = "半導体市況、AI需要で最高値更新",
                text = "https://www.nikkei.com/article/xxxxx",
            )

        val item = parser.parse(intent)

        assertEquals("半導体市況、AI需要で最高値更新", item.title)
        assertEquals("https://www.nikkei.com/article/xxxxx", item.url)
        assertNull(item.sharedText)
    }

    @Test
    fun `a URL embedded within surrounding shared text is extracted, remainder kept as sharedText`() {
        val intent =
            sendIntent(
                subject = "Title",
                text = "来期は車載向けが牽引役になるとの分析。 https://www.nikkei.com/article/xxxxx 参考まで。",
            )

        val item = parser.parse(intent)

        assertEquals("https://www.nikkei.com/article/xxxxx", item.url)
        assertTrue(item.sharedText?.contains("来期は車載向けが牽引役になるとの分析。") == true)
        assertTrue(item.sharedText?.contains("参考まで。") == true)
        assertTrue(item.sharedText?.contains(item.url) == false)
    }

    @Test
    fun `missing subject falls back to the first non-blank line of shared text as title`() {
        val intent = sendIntent(subject = null, text = "半導体市況、AI需要で最高値更新\nhttps://www.nikkei.com/article/xxxxx")

        val item = parser.parse(intent)

        assertEquals("半導体市況、AI需要で最高値更新", item.title)
        assertEquals("https://www.nikkei.com/article/xxxxx", item.url)
    }

    @Test
    fun `no URL anywhere in the shared content never throws, url is empty and editable`() {
        val intent = sendIntent(subject = "Title", text = "no link here")

        val item = parser.parse(intent)

        assertEquals("", item.url)
        assertEquals("no link here", item.sharedText)
    }

    @Test
    fun `completely empty intent never throws, everything is blank`() {
        val intent = sendIntent(subject = null, text = null)

        val item = parser.parse(intent)

        assertEquals("", item.title)
        assertEquals("", item.url)
        assertNull(item.sharedText)
    }

    @Test
    fun `source is always chrome_share and sharedAt comes from the injected clock`() {
        val intent = sendIntent(subject = "Title", text = "https://example.com")

        val item = parser.parse(intent)

        assertEquals("chrome_share", item.source)
        assertEquals(fixedClock.instant(), item.sharedAt)
    }

    private fun sendIntent(
        subject: String?,
        text: String?,
    ): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }
}
