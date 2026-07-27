<?php

declare(strict_types=1);

namespace MaterialCapture\Infrastructure;

use DateTimeImmutable;
use MaterialCapture\Application\PostBodyRendererInterface;
use MaterialCapture\Domain\DraftPayload;

/**
 * Renders post_content from an already-sanitized DraftPayload. Pure formatting —
 * no escaping decisions are made here, since every value has already been sanitized
 * upstream (see docs/security.md#input-handling-wordpress-plugin).
 */
final class PostBodyTemplate implements PostBodyRendererInterface
{
    public function render(DraftPayload $payload, DateTimeImmutable $createdAt): string
    {
        $lines = [
            sprintf('元URL: %s', $payload->url),
            sprintf('保存日時: %s', $createdAt->format(DATE_ATOM)),
        ];

        if ($payload->sharedAt !== null) {
            $lines[] = sprintf('共有日時: %s', $payload->sharedAt->format(DATE_ATOM));
        }

        $lines[] = sprintf('共有元: %s', $payload->source);
        $lines[] = sprintf('メモ: %s', $payload->memo ?? '');

        $body = implode("\n", $lines);

        if ($payload->sharedText !== null && $payload->sharedText !== '') {
            $body .= "\n\n" . $payload->sharedText;
        }

        return $body;
    }
}
