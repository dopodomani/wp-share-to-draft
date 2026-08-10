package io.github.dopodomani.wpsharetodraft.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WordPressSiteUrlTest {
    @Test
    fun `a plain https URL is returned unchanged`() {
        assertEquals("https://example.com", WordPressSiteUrl.normalize("https://example.com"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("https://example.com", WordPressSiteUrl.normalize("  https://example.com  "))
    }

    @Test
    fun `a trailing slash is trimmed`() {
        assertEquals("https://example.com", WordPressSiteUrl.normalize("https://example.com/"))
    }

    @Test
    fun `a path is preserved, only the trailing slash is trimmed`() {
        assertEquals("https://example.com/site", WordPressSiteUrl.normalize("https://example.com/site/"))
    }

    @Test
    fun `plain http is rejected -- HTTPS only`() {
        assertNull(WordPressSiteUrl.normalize("http://example.com"))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(WordPressSiteUrl.normalize("   "))
    }

    @Test
    fun `not a URL at all is rejected`() {
        assertNull(WordPressSiteUrl.normalize("not a url"))
    }

    @Test
    fun `embedded username or password is rejected`() {
        assertNull(WordPressSiteUrl.normalize("https://user:pass@example.com"))
    }

    @Test
    fun `a query string is rejected`() {
        assertNull(WordPressSiteUrl.normalize("https://example.com?foo=bar"))
    }

    @Test
    fun `a fragment is rejected`() {
        assertNull(WordPressSiteUrl.normalize("https://example.com#section"))
    }
}
