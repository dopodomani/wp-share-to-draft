<?php

declare(strict_types=1);

namespace MaterialCapture\Tests\Infrastructure;

use Brain\Monkey\Functions;
use MaterialCapture\Infrastructure\WordPressInputSanitizer;
use MaterialCapture\Tests\BrainMonkeyTestCase;

/**
 * Per docs/phase2-wordpress-plugin-design.md: this suite verifies (a) delegation to the
 * right WordPress function and (b) this plugin's own truncation/default logic. It does
 * NOT re-verify that WordPress's sanitize_* functions themselves strip malicious input
 * correctly — that's WordPress core's own tested behavior, checked against a real
 * WordPress instance in the Phase 4 integration suite instead.
 */
final class WordPressInputSanitizerTest extends BrainMonkeyTestCase
{
    public function test_sanitize_title_delegates_to_sanitize_text_field(): void
    {
        Functions\expect('sanitize_text_field')->once()->with('raw')->andReturn('clean');

        self::assertSame('clean', (new WordPressInputSanitizer())->sanitizeTitle('raw'));
    }

    public function test_sanitize_title_truncates_to_300_characters(): void
    {
        $long = str_repeat('あ', 400);
        Functions\when('sanitize_text_field')->returnArg();

        $result = (new WordPressInputSanitizer())->sanitizeTitle($long);

        self::assertSame(300, mb_strlen($result));
    }

    public function test_sanitize_url_delegates_to_esc_url_raw(): void
    {
        Functions\expect('esc_url_raw')->once()->with('https://example.com')->andReturn('https://example.com/');

        self::assertSame('https://example.com/', (new WordPressInputSanitizer())->sanitizeUrl('https://example.com'));
    }

    public function test_sanitize_memo_delegates_to_sanitize_textarea_field(): void
    {
        Functions\expect('sanitize_textarea_field')->once()->with('raw memo')->andReturn('clean memo');

        self::assertSame('clean memo', (new WordPressInputSanitizer())->sanitizeMemo('raw memo'));
    }

    public function test_sanitize_memo_passes_through_null_without_calling_wordpress(): void
    {
        Functions\expect('sanitize_textarea_field')->never();

        self::assertNull((new WordPressInputSanitizer())->sanitizeMemo(null));
    }

    public function test_sanitize_shared_text_truncates_to_50000_characters(): void
    {
        Functions\when('sanitize_textarea_field')->returnArg();

        $long = str_repeat('a', 60_000);
        $result = (new WordPressInputSanitizer())->sanitizeSharedText($long);

        self::assertSame(50_000, mb_strlen($result));
    }

    public function test_sanitize_source_delegates_to_sanitize_key(): void
    {
        Functions\expect('sanitize_key')->once()->with('Chrome-Share')->andReturn('chrome-share');

        self::assertSame('chrome-share', (new WordPressInputSanitizer())->sanitizeSource('Chrome-Share'));
    }

    public function test_sanitize_source_defaults_to_unknown_when_empty_after_sanitizing(): void
    {
        Functions\when('sanitize_key')->justReturn('');

        self::assertSame('unknown', (new WordPressInputSanitizer())->sanitizeSource('!!!'));
    }

    public function test_sanitize_source_defaults_to_unknown_when_null(): void
    {
        Functions\when('sanitize_key')->justReturn('');

        self::assertSame('unknown', (new WordPressInputSanitizer())->sanitizeSource(null));
    }
}
