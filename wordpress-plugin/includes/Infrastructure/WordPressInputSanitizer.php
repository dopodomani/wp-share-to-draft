<?php

declare(strict_types=1);

namespace MaterialCapture\Infrastructure;

use MaterialCapture\Application\InputSanitizerInterface;
use MaterialCapture\Domain\DraftPayload;

/**
 * The only class that calls WordPress's own sanitize_* functions for capture fields.
 *
 * Per the Phase 2 test plan, unit tests for this class verify delegation to the right
 * WordPress function and this class's own truncation/default logic — not whether
 * WordPress's sanitize functions themselves are "correct" (that's covered by Phase 4
 * integration tests against a real WordPress instance).
 */
final class WordPressInputSanitizer implements InputSanitizerInterface
{
    private const MEMO_MAX_LENGTH = 10_000;
    private const SHARED_TEXT_MAX_LENGTH = 50_000;
    private const SOURCE_MAX_LENGTH = 64;
    private const DEFAULT_SOURCE = 'unknown';

    public function sanitizeTitle(string $value): string
    {
        return $this->truncate(sanitize_text_field($value), DraftPayload::TITLE_MAX_LENGTH);
    }

    public function sanitizeUrl(string $value): string
    {
        return $this->truncate(esc_url_raw($value), DraftPayload::URL_MAX_LENGTH);
    }

    public function sanitizeMemo(?string $value): ?string
    {
        if ($value === null) {
            return null;
        }

        return $this->truncate(sanitize_textarea_field($value), self::MEMO_MAX_LENGTH);
    }

    public function sanitizeSharedText(?string $value): ?string
    {
        if ($value === null) {
            return null;
        }

        return $this->truncate(sanitize_textarea_field($value), self::SHARED_TEXT_MAX_LENGTH);
    }

    public function sanitizeSource(?string $value): string
    {
        $sanitized = sanitize_key((string) $value);

        if ($sanitized === '') {
            return self::DEFAULT_SOURCE;
        }

        return $this->truncate($sanitized, self::SOURCE_MAX_LENGTH);
    }

    private function truncate(string $value, int $maxLength): string
    {
        return mb_substr($value, 0, $maxLength);
    }
}
