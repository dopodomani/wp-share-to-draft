<?php

declare(strict_types=1);

namespace MaterialCapture\Rest;

use MaterialCapture\Application\CreateDraftUseCase;
use MaterialCapture\Application\DraftPayloadFactory;
use MaterialCapture\Domain\Exceptions\CategoryUnavailableException;
use MaterialCapture\Domain\Exceptions\DraftCreationFailedException;
use MaterialCapture\Domain\Exceptions\InvalidPayloadException;
use WP_Error;
use WP_REST_Controller;
use WP_REST_Request;
use WP_REST_Response;
use WP_REST_Server;

/**
 * The only WordPress-aware entry point for material-capture/v1/draft.
 *
 * Depends on the CreateDraftUseCase *interface*, never the concrete CreateDraftService —
 * see docs/phase2-wordpress-plugin-design.md for why (testability without fighting `final`).
 */
final class DraftController extends WP_REST_Controller
{
    protected $namespace = 'material-capture/v1';
    protected $rest_base = 'draft';

    public function __construct(
        private readonly CreateDraftUseCase $useCase,
        private readonly DraftPayloadFactory $payloadFactory,
        private readonly RestResponseFactory $responses,
    ) {
    }

    public function register_routes(): void
    {
        register_rest_route(
            $this->namespace,
            '/' . $this->rest_base,
            [
                [
                    'methods' => WP_REST_Server::CREATABLE,
                    'callback' => [$this, 'create_draft'],
                    'permission_callback' => [$this, 'permission_callback'],
                    'args' => [
                        'title' => [
                            'type' => 'string',
                            'required' => true,
                            'sanitize_callback' => 'sanitize_text_field',
                        ],
                        'url' => [
                            'type' => 'string',
                            'required' => false,
                            'sanitize_callback' => 'esc_url_raw',
                        ],
                        'shared_text' => [
                            'type' => 'string',
                            'required' => false,
                            'sanitize_callback' => 'sanitize_textarea_field',
                        ],
                        'memo' => [
                            'type' => 'string',
                            'required' => false,
                            'sanitize_callback' => 'sanitize_textarea_field',
                        ],
                        'source' => [
                            'type' => 'string',
                            'required' => false,
                            'sanitize_callback' => 'sanitize_key',
                        ],
                        'shared_at' => [
                            'type' => 'string',
                            'required' => false,
                        ],
                    ],
                ],
            ]
        );
    }

    /**
     * See docs/phase2-wordpress-plugin-design.md#authentication--authorization-division-of-responsibility:
     * a missing/invalid Application Password is WordPress core's concern, handled before this
     * method is ever reached for the "invalid credentials supplied" case. This method only owns
     * the HTTPS precondition and the edit_posts capability check.
     */
    public function permission_callback(WP_REST_Request $request): bool|WP_Error
    {
        if (!is_ssl()) {
            return $this->responses->error(
                'https_required',
                'This endpoint requires HTTPS.',
                400
            );
        }

        if (!current_user_can('edit_posts')) {
            return $this->responses->error(
                'insufficient_capability',
                'The authenticated user does not have permission to create posts.',
                is_user_logged_in() ? 403 : 401
            );
        }

        return true;
    }

    public function create_draft(WP_REST_Request $request): WP_REST_Response|WP_Error
    {
        try {
            $payload = $this->payloadFactory->fromArray($request->get_params());
        } catch (InvalidPayloadException $exception) {
            return $this->responses->error($exception->errorCode(), $exception->getMessage(), 400);
        }

        try {
            $result = $this->useCase->create($payload, get_current_user_id());
        } catch (CategoryUnavailableException $exception) {
            return $this->responses->error('category_unavailable', $exception->getMessage(), 409);
        } catch (DraftCreationFailedException $exception) {
            return $this->responses->error('insert_failed', $exception->getMessage(), 500);
        }

        return $this->responses->success($result);
    }
}
