<?php

declare(strict_types=1);

namespace MaterialCapture\Tests\Application;

use MaterialCapture\Application\DraftPayloadFactory;
use MaterialCapture\Application\InputSanitizerInterface;
use MaterialCapture\Domain\Exceptions\InvalidPayloadException;
use Mockery;
use Mockery\Adapter\Phpunit\MockeryPHPUnitIntegration;
use PHPUnit\Framework\TestCase;

final class DraftPayloadFactoryTest extends TestCase
{
    use MockeryPHPUnitIntegration;

    public function test_each_field_is_routed_through_the_matching_sanitizer_method(): void
    {
        $sanitizer = Mockery::mock(InputSanitizerInterface::class);
        $sanitizer->shouldReceive('sanitizeTitle')->once()->with('raw title')->andReturn('Raw Title');
        $sanitizer->shouldReceive('sanitizeUrl')->once()->with('https://example.com')->andReturn('https://example.com');
        $sanitizer->shouldReceive('sanitizeSharedText')->once()->with('raw shared text')->andReturn('shared text');
        $sanitizer->shouldReceive('sanitizeMemo')->once()->with('raw memo')->andReturn('memo');
        $sanitizer->shouldReceive('sanitizeSource')->once()->with('chrome-share')->andReturn('chrome_share');

        $factory = new DraftPayloadFactory($sanitizer);

        $payload = $factory->fromArray([
            'title' => 'raw title',
            'url' => 'https://example.com',
            'shared_text' => 'raw shared text',
            'memo' => 'raw memo',
            'source' => 'chrome-share',
        ]);

        self::assertSame('Raw Title', $payload->title);
        self::assertSame('shared text', $payload->sharedText);
        self::assertSame('memo', $payload->memo);
        self::assertSame('chrome_share', $payload->source);
        self::assertNull($payload->sharedAt);
    }

    public function test_missing_optional_fields_pass_null_to_the_sanitizer(): void
    {
        $sanitizer = Mockery::mock(InputSanitizerInterface::class);
        $sanitizer->shouldReceive('sanitizeTitle')->once()->andReturn('Title');
        $sanitizer->shouldReceive('sanitizeUrl')->once()->andReturn('https://example.com');
        $sanitizer->shouldReceive('sanitizeSharedText')->once()->with(null)->andReturn(null);
        $sanitizer->shouldReceive('sanitizeMemo')->once()->with(null)->andReturn(null);
        $sanitizer->shouldReceive('sanitizeSource')->once()->with(null)->andReturn('unknown');

        $factory = new DraftPayloadFactory($sanitizer);

        $payload = $factory->fromArray(['title' => 'Title', 'url' => 'https://example.com']);

        self::assertSame('unknown', $payload->source);
    }

    /** @dataProvider validSharedAtValues */
    public function test_valid_shared_at_formats_are_accepted(string $value): void
    {
        $sanitizer = $this->passthroughSanitizer();
        $factory = new DraftPayloadFactory($sanitizer);

        $payload = $factory->fromArray([
            'title' => 'Title',
            'url' => 'https://example.com',
            'shared_at' => $value,
        ]);

        self::assertNotNull($payload->sharedAt);
    }

    /** @return array<string, array{0: string}> */
    public static function validSharedAtValues(): array
    {
        return [
            'with positive offset' => ['2026-07-27T09:15:00+09:00'],
            'with negative offset' => ['2026-07-27T09:15:00-05:00'],
            'with Z suffix' => ['2026-07-27T00:15:00Z'],
            'with fractional seconds' => ['2026-07-27T09:15:00.123456+09:00'],
        ];
    }

    /** @dataProvider invalidSharedAtValues */
    public function test_invalid_shared_at_formats_are_rejected(string $value): void
    {
        $sanitizer = $this->passthroughSanitizer();
        $factory = new DraftPayloadFactory($sanitizer);

        try {
            $factory->fromArray(['title' => 'Title', 'url' => 'https://example.com', 'shared_at' => $value]);
            self::fail('Expected InvalidPayloadException.');
        } catch (InvalidPayloadException $exception) {
            self::assertSame('invalid_shared_at', $exception->errorCode());
        }
    }

    /** @return array<string, array{0: string}> */
    public static function invalidSharedAtValues(): array
    {
        return [
            'no offset at all' => ['2026-07-27T09:15:00'],
            'date only' => ['2026-07-27'],
            'not a timestamp' => ['not-a-date'],
            'relative time' => ['now'],
        ];
    }

    private function passthroughSanitizer(): InputSanitizerInterface
    {
        $sanitizer = Mockery::mock(InputSanitizerInterface::class);
        $sanitizer->shouldReceive('sanitizeTitle')->andReturnUsing(static fn (string $v) => $v);
        $sanitizer->shouldReceive('sanitizeUrl')->andReturnUsing(static fn (string $v) => $v);
        $sanitizer->shouldReceive('sanitizeSharedText')->andReturnUsing(static fn (?string $v) => $v);
        $sanitizer->shouldReceive('sanitizeMemo')->andReturnUsing(static fn (?string $v) => $v);
        $sanitizer->shouldReceive('sanitizeSource')->andReturn('unknown');

        return $sanitizer;
    }
}
