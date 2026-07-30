<?php

declare(strict_types=1);

namespace MaterialCapture\XmlRpc;

use IXR_Error;
use MaterialCapture\Application\CreateDraftUseCase;
use MaterialCapture\Application\DraftPayloadFactory;
use MaterialCapture\Domain\Exceptions\CategoryUnavailableException;
use MaterialCapture\Domain\Exceptions\DraftCreationFailedException;
use MaterialCapture\Domain\Exceptions\InvalidPayloadException;
use wp_xmlrpc_server;

/**
 * A second thin adapter over CreateDraftUseCase/DraftPayloadFactory, mirroring
 * Rest\DraftController -- see docs/phase2c-xmlrpc-design.md#layering. Exists because at
 * least one real production host doesn't forward the Authorization header to PHP, so
 * REST's Application-Password-over-Basic-Auth can't authenticate there at all.
 */
final class DraftXmlRpcHandler
{
    public function __construct(
        private readonly CreateDraftUseCase $useCase,
        private readonly DraftPayloadFactory $payloadFactory,
    ) {
    }

    /**
     * Registers this handler's method with WordPress's XML-RPC server. Hooked to the
     * `xmlrpc_methods` filter from Plugin.php, mirroring how DraftController registers
     * itself on `rest_api_init`.
     *
     * @param array<string, mixed> $methods
     * @return array<string, mixed>
     */
    public function registerMethod(array $methods): array
    {
        $methods['material_capture.createDraft'] = [$this, 'createDraft'];

        return $methods;
    }

    /**
     * Handles a `material_capture.createDraft` XML-RPC call.
     *
     * @param array<int, mixed> $args Positional params per docs/api-spec.md's XML-RPC
     *   section: [username, applicationPassword, title, url, sharedText, memo, source, sharedAt]
     * @return array<string, mixed>|IXR_Error
     */
    public function createDraft(array $args, wp_xmlrpc_server $server): array|IXR_Error
    {
        [$username, $password, $title, $url, $sharedText, $memo, $source, $sharedAt] =
            array_pad($args, 8, null);

        // WordPress core's own credential check -- Application Passwords work here
        // natively, not just for REST. A failure here is WordPress core's error, not
        // ours -- see docs/security.md's division of responsibility.
        if (!$server->login((string) $username, (string) $password)) {
            return $server->error;
        }

        if (!is_ssl()) {
            return new IXR_Error(400, 'This endpoint requires HTTPS.');
        }
        if (!current_user_can('edit_posts')) {
            return new IXR_Error(403, 'The authenticated user does not have permission to create posts.');
        }

        try {
            $payload = $this->payloadFactory->fromArray([
                'title' => $title,
                'url' => $url,
                'shared_text' => $sharedText,
                'memo' => $memo,
                'source' => $source,
                'shared_at' => $sharedAt,
            ]);
        } catch (InvalidPayloadException $exception) {
            return new IXR_Error(400, $exception->getMessage());
        }

        try {
            $result = $this->useCase->create($payload, get_current_user_id());
        } catch (CategoryUnavailableException $exception) {
            return new IXR_Error(409, $exception->getMessage());
        } catch (DraftCreationFailedException $exception) {
            return new IXR_Error(500, $exception->getMessage());
        }

        return [
            'post_id' => $result->postId,
            'status' => $result->status,
            'title' => $result->title,
            'edit_url' => $result->editUrl,
            'preview_url' => $result->previewUrl,
            'category' => $result->category,
            'created_at' => $result->createdAt->format(DATE_ATOM),
        ];
    }
}
