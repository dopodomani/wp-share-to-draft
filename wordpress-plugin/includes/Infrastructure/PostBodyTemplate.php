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
        $lines = [];

        if ($payload->url !== '') {
            $lines[] = sprintf('元URL: %s', $payload->url);
        }

        $lines[] = sprintf('保存日時: %s', $createdAt->format(DATE_ATOM));

        if ($payload->sharedAt !== null) {
            $lines[] = sprintf('共有日時: %s', $payload->sharedAt->format(DATE_ATOM));
        }

        $lines[] = sprintf('共有元: %s', $payload->source);
        $lines[] = sprintf('メモ: %s', $payload->memo ?? '');

        $body = implode("\n", $lines);

        if ($payload->sharedText !== null && $payload->sharedText !== '') {
            $sharedText = $this->removeRepeatedTitleLine($payload->sharedText, $payload->title);
            if ($sharedText !== '') {
                $body .= "\n\n" . $sharedText;
            }
        }

        return $body;
    }

    /**
     * Android keeps sharedText as the URL-stripped source text. When its first meaningful
     * line was also used as the title, omit that line from the rendered body only. The API
     * field remains unchanged, and an edited/non-matching title never removes user content.
     */
    private function removeRepeatedTitleLine(string $sharedText, string $title): string
    {
        $lines = preg_split("/\r\n|\r|\n/", $sharedText) ?: [];
        $index = 0;
        while ($index < count($lines) && trim($this->comparisonText($lines[$index])) === '') {
            $index++;
        }

        while ($index < count($lines) && $this->isLinkLabel($lines[$index])) {
            $index++;
            while ($index < count($lines) && trim($this->comparisonText($lines[$index])) === '') {
                $index++;
            }
        }

        if ($index >= count($lines) || $this->comparisonText($lines[$index]) !== $this->comparisonText($title)) {
            return $sharedText;
        }

        $remaining = array_slice($lines, $index + 1);
        return trim(implode("\n", $remaining));
    }

    private function isLinkLabel(string $line): bool
    {
        return preg_match('/^\s*(リンク|link)\s*[:：]\s*[/／]?\s*(を含む|including)?\s*$/iu', $this->comparisonText($line)) === 1;
    }

    private function comparisonText(string $value): string
    {
        $value = str_replace(["\xC2\xA0", "\xE3\x80\x80"], ' ', $value);
        $value = preg_replace('/\s+/u', ' ', trim($value)) ?? trim($value);
        $value = function_exists('mb_substr') ? mb_substr($value, 0, DraftPayload::TITLE_MAX_LENGTH) : substr($value, 0, DraftPayload::TITLE_MAX_LENGTH);
        return $value;
    }
}
