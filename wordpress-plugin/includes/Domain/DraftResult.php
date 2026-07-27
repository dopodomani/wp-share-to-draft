<?php

declare(strict_types=1);

namespace MaterialCapture\Domain;

use DateTimeImmutable;

/**
 * The outcome of a successful draft creation, shaped to match the `201` response body
 * documented in docs/api-spec.md.
 */
final class DraftResult
{
    public function __construct(
        public readonly int $postId,
        public readonly string $status,
        public readonly string $title,
        public readonly ?string $editUrl,
        public readonly ?string $previewUrl,
        public readonly string $category,
        public readonly DateTimeImmutable $createdAt,
    ) {
    }
}
