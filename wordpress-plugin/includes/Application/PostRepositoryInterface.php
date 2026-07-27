<?php

declare(strict_types=1);

namespace MaterialCapture\Application;

use MaterialCapture\Domain\Exceptions\DraftCreationFailedException;

/**
 * Port for post/category persistence. Implemented by Infrastructure\WpPostRepository.
 *
 * `insertDraft()` deliberately has no status parameter: draft is the only outcome this
 * repository is capable of producing, so a client can never request `publish` by any
 * combination of arguments.
 */
interface PostRepositoryInterface
{
    /**
     * Ensures the named category exists, creating it if necessary. Called ONLY from
     * Plugin::activate() — never during request handling.
     */
    public function ensureCategoryOnActivation(string $name): int;

    /**
     * Reads the previously-stored category id and re-verifies it still exists.
     * Returns null if it was never configured or the term has since been deleted —
     * this method never creates or re-creates the category.
     */
    public function resolveConfiguredCategoryId(): ?int;

    /**
     * Persists the draft post, always as status=draft.
     *
     * @throws DraftCreationFailedException
     */
    public function insertDraft(string $title, string $body, int $categoryId, int $authorId): int;

    public function editLink(int $postId): ?string;

    public function previewLink(int $postId): ?string;
}
