<?php

declare(strict_types=1);

namespace MaterialCapture\Domain;

use DateTimeImmutable;
use MaterialCapture\Domain\Exceptions\InvalidPayloadException;

/**
 * A validated, already-sanitized capture submission.
 *
 * Deliberately dependency-free: no WordPress functions, no InputSanitizer. Sanitization
 * happens once, upstream, in Application\DraftPayloadFactory. This class only enforces the
 * shape/invariants a payload must have to be considered valid at all — see docs/architecture.md
 * and docs/phase2-wordpress-plugin-design.md for the rationale.
 */
final class DraftPayload
{
    public const TITLE_MAX_LENGTH = 300;
    public const URL_MAX_LENGTH = 2048;

    private function __construct(
        public readonly string $title,
        public readonly string $url,
        public readonly ?string $sharedText,
        public readonly ?string $memo,
        public readonly string $source,
        public readonly ?DateTimeImmutable $sharedAt,
    ) {
    }

    /**
     * Validates and constructs a payload from already-sanitized values.
     *
     * @throws InvalidPayloadException
     */
    public static function create(
        string $title,
        string $url,
        ?string $sharedText,
        ?string $memo,
        string $source,
        ?DateTimeImmutable $sharedAt,
    ): self {
        $title = trim($title);
        if ($title === '') {
            throw InvalidPayloadException::missingRequiredField('title');
        }

        // url is optional -- see docs/tech-decisions.md#12-url-is-optional. An empty url
        // skips format validation entirely; a non-empty one must still be a plausible
        // absolute http(s) URL.
        $url = trim($url);
        if ($url !== '' && !self::isValidHttpUrl($url)) {
            throw InvalidPayloadException::invalidUrl();
        }

        return new self($title, $url, $sharedText, $memo, $source, $sharedAt);
    }

    private static function isValidHttpUrl(string $url): bool
    {
        if (filter_var($url, FILTER_VALIDATE_URL) === false) {
            return false;
        }

        // phpcs:ignore WordPress.WP.AlternativeFunctions.parse_url_parse_url -- Domain is deliberately WordPress-free (see docs/phase2-wordpress-plugin-design.md); wp_parse_url() would introduce a WP dependency here.
        $scheme = parse_url($url, PHP_URL_SCHEME);

        return $scheme === 'http' || $scheme === 'https';
    }
}
