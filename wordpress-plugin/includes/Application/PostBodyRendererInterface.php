<?php

declare(strict_types=1);

namespace MaterialCapture\Application;

use DateTimeImmutable;
use MaterialCapture\Domain\DraftPayload;

/**
 * Port for rendering post_content from a DraftPayload. Implemented by
 * Infrastructure\PostBodyTemplate.
 */
interface PostBodyRendererInterface
{
    /**
     * Renders the post body for a draft.
     *
     * @param DateTimeImmutable $createdAt Server-side creation time (the "保存日時" line) —
     *   distinct from $payload->sharedAt, which is the client-reported "共有日時", if present.
     */
    public function render(DraftPayload $payload, DateTimeImmutable $createdAt): string;
}
