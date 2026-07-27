<?php

declare(strict_types=1);

namespace MaterialCapture\Domain\Exceptions;

use RuntimeException;
use Throwable;

/**
 * Thrown when the underlying post store fails to create the draft (e.g. wp_insert_post
 * returns a WP_Error). Maps to `500 insert_failed` — see docs/api-spec.md.
 */
final class DraftCreationFailedException extends RuntimeException
{
    public function __construct(string $reason, ?Throwable $previous = null)
    {
        parent::__construct(sprintf('Failed to create the draft post: %s', $reason), 0, $previous);
    }
}
