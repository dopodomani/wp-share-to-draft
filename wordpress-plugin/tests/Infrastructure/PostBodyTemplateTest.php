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
}
