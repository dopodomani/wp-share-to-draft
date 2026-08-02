<?php

declare(strict_types=1);

namespace MaterialCapture\Tests\Domain;

use DateTimeImmutable;
use MaterialCapture\Domain\DraftPayload;
use MaterialCapture\Domain\Exceptions\InvalidPayloadException;
use PHPUnit\Framework\TestCase;

final class DraftPayloadTest extends TestCase
{
    public function test_valid_input_constructs_successfully(): void
    {
        $sharedAt = new DateTimeImmutable('2026-07-27T09:15:00+09:00');

        $payload = DraftPayload::create(
            '半導体市況、AI需要で最高値更新',
            'https://www.nikkei.com/article/xxxxx',
            '来期は車載向けが牽引役になるとの分析。',
            '車載半導体の記事と合わせて読む',
            'chrome_share',
            $sharedAt,
        );

        self::assertSame('半導体市況、AI需要で最高値更新', $payload->title);
        self::assertSame('https://www.nikkei.com/article/xxxxx', $payload->url);
        self::assertSame($sharedAt, $payload->sharedAt);
    }

    public function test_empty_title_throws_missing_required_field(): void
    {
        try {
            DraftPayload::create('   ', 'https://example.com', null, null, 'unknown', null);
            self::fail('Expected InvalidPayloadException.');
        } catch (InvalidPayloadException $exception) {
            self::assertSame('missing_required_field', $exception->errorCode());
        }
    }

    public function test_empty_url_is_accepted(): void
    {
        $payload = DraftPayload::create('Title', '  ', null, null, 'unknown', null);

        self::assertSame('', $payload->url);
    }

    /** @dataProvider malformedUrls */
    public function test_malformed_url_throws_invalid_url(string $url): void
    {
        try {
            DraftPayload::create('Title', $url, null, null, 'unknown', null);
            self::fail('Expected InvalidPayloadException.');
        } catch (InvalidPayloadException $exception) {
            self::assertSame('invalid_url', $exception->errorCode());
        }
    }

    /** @return array<string, array{0: string}> */
    public static function malformedUrls(): array
    {
        return [
            'not a url at all' => ['not-a-url'],
            'missing scheme' => ['www.example.com/article'],
            'javascript scheme' => ['javascript:alert(1)'],
            'ftp scheme' => ['ftp://example.com/file'],
        ];
    }

    public function test_optional_fields_may_be_null(): void
    {
        $payload = DraftPayload::create('Title', 'https://example.com', null, null, 'unknown', null);

        self::assertNull($payload->sharedText);
        self::assertNull($payload->memo);
        self::assertNull($payload->sharedAt);
        self::assertSame('unknown', $payload->source);
    }
}
