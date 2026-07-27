<?php

declare(strict_types=1);

namespace MaterialCapture\Infrastructure;

use MaterialCapture\Application\PostRepositoryInterface;
use MaterialCapture\Domain\Exceptions\DraftCreationFailedException;
use WP_Error;

/**
 * The only class in this plugin that calls WordPress core post/term functions.
 *
 * Category lifecycle (see docs/phase2-wordpress-plugin-design.md#category-lifecycle):
 * created once at activation, id cached in an option, re-verified (never re-created)
 * on every request, never deleted by this plugin.
 */
final class WpPostRepository implements PostRepositoryInterface
{
    public const OPTION_CATEGORY_ID = 'material_capture_category_id';

    private const TAXONOMY = 'category';

    public function ensureCategoryOnActivation(string $name): int
    {
        $existing = term_exists($name, self::TAXONOMY);

        if ($existing) {
            $termId = (int) (is_array($existing) ? $existing['term_id'] : $existing);
        } else {
            $inserted = wp_insert_term($name, self::TAXONOMY);

            if (is_wp_error($inserted)) {
                // phpcs:ignore WordPress.Security.EscapeOutput.ExceptionNotEscaped -- internal exception message, never echoed as HTML; this API is JSON-only (see docs/api-spec.md).
                throw new DraftCreationFailedException($inserted->get_error_message());
            }

            $termId = (int) $inserted['term_id'];
        }

        update_option(self::OPTION_CATEGORY_ID, $termId);

        return $termId;
    }

    public function resolveConfiguredCategoryId(): ?int
    {
        $stored = get_option(self::OPTION_CATEGORY_ID, null);

        if ($stored === null || $stored === false || $stored === '') {
            return null;
        }

        $termId = (int) $stored;

        if (!term_exists($termId, self::TAXONOMY)) {
            return null;
        }

        return $termId;
    }

    public function insertDraft(string $title, string $body, int $categoryId, int $authorId): int
    {
        $result = wp_insert_post(
            [
                'post_title' => $title,
                'post_content' => $body,
                'post_status' => 'draft',
                'post_author' => $authorId,
                'post_category' => [$categoryId],
            ],
            true
        );

        if ($result instanceof WP_Error) {
            // phpcs:ignore WordPress.Security.EscapeOutput.ExceptionNotEscaped -- internal exception message, never echoed as HTML; this API is JSON-only (see docs/api-spec.md).
            throw new DraftCreationFailedException($result->get_error_message());
        }

        return (int) $result;
    }

    public function editLink(int $postId): ?string
    {
        $link = get_edit_post_link($postId, 'raw');

        return $link !== '' && $link !== null ? $link : null;
    }

    public function previewLink(int $postId): ?string
    {
        $link = get_preview_post_link($postId);

        return $link !== '' && $link !== null ? $link : null;
    }
}
