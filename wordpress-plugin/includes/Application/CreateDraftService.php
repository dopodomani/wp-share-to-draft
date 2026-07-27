<?php

declare(strict_types=1);

namespace MaterialCapture\Application;

use DateTimeImmutable;
use MaterialCapture\Domain\DraftPayload;
use MaterialCapture\Domain\DraftResult;
use MaterialCapture\Domain\Exceptions\CategoryUnavailableException;

final class CreateDraftService implements CreateDraftUseCase
{
    private const TITLE_PREFIX = '[INBOX] ';

    public function __construct(
        private readonly PostRepositoryInterface $posts,
        private readonly PostBodyRendererInterface $bodyRenderer,
    ) {
    }

    public function create(DraftPayload $payload, int $authorId, ?DateTimeImmutable $now = null): DraftResult
    {
        $now ??= new DateTimeImmutable();

        $categoryId = $this->posts->resolveConfiguredCategoryId();
        if ($categoryId === null) {
            throw new CategoryUnavailableException();
        }

        $title = $this->withInboxPrefix($payload->title);
        $body = $this->bodyRenderer->render($payload, $now);

        $postId = $this->posts->insertDraft($title, $body, $categoryId, $authorId);

        return new DraftResult(
            $postId,
            'draft',
            $title,
            $this->posts->editLink($postId),
            $this->posts->previewLink($postId),
            MaterialCategory::NAME,
            $now,
        );
    }

    private function withInboxPrefix(string $title): string
    {
        return str_starts_with($title, self::TITLE_PREFIX) ? $title : self::TITLE_PREFIX . $title;
    }
}
