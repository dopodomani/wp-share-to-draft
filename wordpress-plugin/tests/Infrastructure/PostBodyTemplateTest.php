<?php

declare(strict_types=1);

namespace MaterialCapture\Tests\Infrastructure;

use DateTimeImmutable;
use MaterialCapture\Domain\DraftPayload;
use MaterialCapture\Infrastructure\PostBodyTemplate;
use PHPUnit\Framework\TestCase;

/**
 * Pure formatting, no WordPress functions involved -- see PostBodyTemplate's own docblock.
 * Plain PHPUnit, no Brain\Monkey needed.
 */
final class PostBodyTemplateTest extends TestCase
{
    public function test_url_line_is_included_when_url_is_present(): void
    {
        $payload = DraftPayload::create('Title', 'https://example.com', null, null, 'unknown', null);
        $now = new DateTimeImmutable('2026-07-27T09:15:03+09:00');

        $body = (new PostBodyTemplate())->render($payload, $now);

        self::assertStringContainsString('元URL: https://example.com', $body);
    }

    /**
     * url is optional -- see docs/tech-decisions.md#12-url-is-optional. A memo-only capture
     * (e.g. sharing a Chrome text selection with no detectable source URL) shouldn't render a
     * dangling "元URL: " line with nothing after it.
     */
    public function test_url_line_is_omitted_when_url_is_empty(): void
    {
        $payload = DraftPayload::create('Title', '', null, null, 'unknown', null);
        $now = new DateTimeImmutable('2026-07-27T09:15:03+09:00');

        $body = (new PostBodyTemplate())->render($payload, $now);

        self::assertStringNotContainsString('元URL', $body);
    }

    public function test_other_lines_are_still_rendered_when_url_is_empty(): void
    {
        $payload = DraftPayload::create('Title', '', 'shared text', 'my memo', 'chrome_share', null);
        $now = new DateTimeImmutable('2026-07-27T09:15:03+09:00');

        $body = (new PostBodyTemplate())->render($payload, $now);

        self::assertStringContainsString('共有元: chrome_share', $body);
        self::assertStringContainsString('メモ: my memo', $body);
        self::assertStringContainsString('shared text', $body);
    }

    public function test_repeated_title_line_is_removed_from_shared_text(): void
    {
        $payload = DraftPayload::create('Article title', '', "Article title\n\nBody", null, 'chrome_share', null);

        $body = (new PostBodyTemplate())->render($payload, new DateTimeImmutable('2026-07-27T09:15:03+09:00'));

        self::assertStringNotContainsString("\n\nArticle title\n", $body);
        self::assertStringContainsString("\n\nBody", $body);
    }

    public function test_repeated_title_comparison_ignores_nbsp_and_leading_blank_lines(): void
    {
        $payload = DraftPayload::create('Article title', '', "\nArticle\xC2\xA0\xC2\xA0title\r\nBody", null, 'chrome_share', null);

        $body = (new PostBodyTemplate())->render($payload, new DateTimeImmutable('2026-07-27T09:15:03+09:00'));

        self::assertStringNotContainsString('Article', $body);
        self::assertStringContainsString('Body', $body);
    }

    public function test_title_only_shared_text_does_not_add_an_empty_body_section(): void
    {
        $payload = DraftPayload::create('Article title', '', 'Article title', null, 'chrome_share', null);

        $body = (new PostBodyTemplate())->render($payload, new DateTimeImmutable('2026-07-27T09:15:03+09:00'));

        self::assertStringNotContainsString("\n\nArticle title", $body);
    }

    public function test_non_matching_shared_text_is_preserved(): void
    {
        $payload = DraftPayload::create('Edited title', '', "Original title\nBody", null, 'chrome_share', null);

        $body = (new PostBodyTemplate())->render($payload, new DateTimeImmutable('2026-07-27T09:15:03+09:00'));

        self::assertStringContainsString('Original title', $body);
    }
}
