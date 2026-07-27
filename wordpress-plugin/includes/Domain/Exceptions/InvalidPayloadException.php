<?php

declare(strict_types=1);

namespace MaterialCapture\Domain\Exceptions;

use RuntimeException;

/**
 * Thrown when a draft submission fails validation.
 *
 * The $errorCode matches a `code` value documented in docs/api-spec.md exactly,
 * so the REST controller can map it to a status/body without re-deriving the mapping.
 */
final class InvalidPayloadException extends RuntimeException
{
    public function __construct(private readonly string $errorCode, string $message)
    {
        parent::__construct($message);
    }

    public static function missingRequiredField(string $field): self
    {
        return new self('missing_required_field', sprintf('The "%s" field is required.', $field));
    }

    public static function invalidUrl(): self
    {
        return new self('invalid_url', 'The provided url is not a valid absolute http(s) URL.');
    }

    public static function invalidSharedAt(): self
    {
        return new self(
            'invalid_shared_at',
            'The shared_at field must be an RFC 3339 timestamp with an explicit offset or "Z".'
        );
    }

    public function errorCode(): string
    {
        return $this->errorCode;
    }
}
