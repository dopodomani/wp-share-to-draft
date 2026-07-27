<?php

declare(strict_types=1);

namespace MaterialCapture\Rest;

use MaterialCapture\Domain\DraftResult;
use WP_Error;
use WP_REST_Response;

/**
 * Builds the success/error JSON shapes documented in docs/api-spec.md. Kept separate from
 * DraftController so the response shape can be unit-tested without a REST request in play.
 */
final class RestResponseFactory
{
    public function success(DraftResult $result): WP_REST_Response
    {
        return new WP_REST_Response(
            [
                'post_id' => $result->postId,
                'status' => $result->status,
                'title' => $result->title,
                'edit_url' => $result->editUrl,
                'preview_url' => $result->previewUrl,
                'category' => $result->category,
                'created_at' => $result->createdAt->format(DATE_ATOM),
            ],
            201
        );
    }

    public function error(string $code, string $message, int $status): WP_Error
    {
        return new WP_Error($code, $message, ['status' => $status]);
    }
}
