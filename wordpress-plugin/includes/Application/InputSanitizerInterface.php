<?php

declare(strict_types=1);

namespace MaterialCapture\Application;

/**
 * Port for per-field sanitization. Implemented by Infrastructure\WordPressInputSanitizer.
 */
interface InputSanitizerInterface
{
    public function sanitizeTitle(string $value): string;

    public function sanitizeUrl(string $value): string;

    public function sanitizeMemo(?string $value): ?string;

    public function sanitizeSharedText(?string $value): ?string;

    /** Empty or fully-stripped input resolves to 'unknown'. */
    public function sanitizeSource(?string $value): string;
}
