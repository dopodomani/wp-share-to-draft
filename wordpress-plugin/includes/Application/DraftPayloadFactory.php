<?php

declare(strict_types=1);

namespace MaterialCapture\Application;

use DateTimeImmutable;
use Exception;
use MaterialCapture\Domain\DraftPayload;
use MaterialCapture\Domain\Exceptions\InvalidPayloadException;

/**
 * Turns a raw associative array (REST request params) into a validated DraftPayload.
 *
 * This is the only place InputSanitizerInterface is called from — DraftPayload itself
 * never sees an unsanitized value and never sees the sanitizer.
 */
final class DraftPayloadFactory
{
    /** Fixed-offset or `Z`-suffixed RFC 3339 timestamp — ambiguous/offset-less values are rejected. */
    private const SHARED_AT_PATTERN =
        '/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$/';

    public function __construct(private readonly InputSanitizerInterface $sanitizer)
    {
    }

    /**
     * Sanitizes and validates a raw request array into a DraftPayload.
     *
     * @param array<string, mixed> $data
     * @throws InvalidPayloadException
     */
    public function fromArray(array $data): DraftPayload
    {
        $title = $this->sanitizer->sanitizeTitle($this->stringOrEmpty($data['title'] ?? null));
        $url = $this->sanitizer->sanitizeUrl($this->stringOrEmpty($data['url'] ?? null));
        $sharedText = $this->sanitizer->sanitizeSharedText($this->nullableString($data['shared_text'] ?? null));
        $memo = $this->sanitizer->sanitizeMemo($this->nullableString($data['memo'] ?? null));
        $source = $this->sanitizer->sanitizeSource($this->nullableString($data['source'] ?? null));
        $sharedAt = $this->parseSharedAt($this->nullableString($data['shared_at'] ?? null));

        return DraftPayload::create($title, $url, $sharedText, $memo, $source, $sharedAt);
    }

    /**
     * Parses shared_at, requiring a fixed-offset or Z-suffixed RFC 3339 timestamp.
     *
     * @throws InvalidPayloadException
     */
    private function parseSharedAt(?string $value): ?DateTimeImmutable
    {
        if ($value === null || $value === '') {
            return null;
        }

        if (preg_match(self::SHARED_AT_PATTERN, $value) !== 1) {
            throw InvalidPayloadException::invalidSharedAt();
        }

        try {
            return new DateTimeImmutable($value);
        } catch (Exception $exception) {
            throw InvalidPayloadException::invalidSharedAt();
        }
    }

    private function stringOrEmpty(mixed $value): string
    {
        return is_string($value) ? $value : '';
    }

    private function nullableString(mixed $value): ?string
    {
        if ($value === null) {
            return null;
        }

        return is_string($value) ? $value : null;
    }
}
