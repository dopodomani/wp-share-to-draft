<?php

declare(strict_types=1);

namespace MaterialCapture\Domain\Exceptions;

use RuntimeException;

/**
 * Thrown when the category configured at plugin activation is missing or was deleted.
 * Maps to `409 category_unavailable` — see docs/api-spec.md.
 */
final class CategoryUnavailableException extends RuntimeException
{
    public function __construct()
    {
        parent::__construct('The configured material category is unavailable.');
    }
}
