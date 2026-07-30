<?php

declare(strict_types=1);

namespace MaterialCapture;

use MaterialCapture\Application\CreateDraftService;
use MaterialCapture\Application\DraftPayloadFactory;
use MaterialCapture\Application\MaterialCategory;
use MaterialCapture\Infrastructure\PostBodyTemplate;
use MaterialCapture\Infrastructure\WordPressInputSanitizer;
use MaterialCapture\Infrastructure\WpPostRepository;
use MaterialCapture\Rest\DraftController;
use MaterialCapture\Rest\RestResponseFactory;
use MaterialCapture\XmlRpc\DraftXmlRpcHandler;

/**
 * Composition root. No DI container — the object graph here is small enough that manual
 * wiring in one place is easier to read and audit than introducing a container
 * (see docs/tech-decisions.md#6-dependency-injection).
 */
final class Plugin
{
    /**
     * Hooked to register_activation_hook(). Ensures the material category exists and
     * caches its id. This is the ONLY place a category is created — never during a request.
     */
    public static function activate(): void
    {
        (new WpPostRepository())->ensureCategoryOnActivation(MaterialCategory::NAME);
    }

    /**
     * Hooked to register_deactivation_hook(). Deliberately a no-op: deactivating must not
     * touch posts, categories, or options (see docs/security.md#threat-model-summary).
     */
    public static function deactivate(): void
    {
    }

    /**
     * Hooked to rest_api_init. Wires the object graph and registers the REST route.
     */
    public function registerRoutes(): void
    {
        $sanitizer = new WordPressInputSanitizer();
        $payloadFactory = new DraftPayloadFactory($sanitizer);
        $useCase = new CreateDraftService(new WpPostRepository(), new PostBodyTemplate());

        $controller = new DraftController($useCase, $payloadFactory, new RestResponseFactory());
        $controller->register_routes();
    }

    /**
     * Called unconditionally from the plugin bootstrap (no wrapping action -- WordPress core
     * has no `xmlrpc_init` action; `xmlrpc_methods` is a plain filter applied only when
     * xmlrpc.php itself constructs its server, so registering it early/always is cheap and
     * correct, exactly like registerRoutes()'s `rest_api_init` registration). Wires the same
     * object graph into a second, XML-RPC adapter over the identical
     * CreateDraftUseCase/DraftPayloadFactory -- see docs/phase2c-xmlrpc-design.md#layering.
     * Registers unconditionally (no wp_is_application_passwords_available() pre-check); a
     * disabled/misconfigured Application Passwords setup surfaces as a normal login() failure
     * instead.
     */
    public function registerXmlRpcMethods(): void
    {
        $sanitizer = new WordPressInputSanitizer();
        $payloadFactory = new DraftPayloadFactory($sanitizer);
        $useCase = new CreateDraftService(new WpPostRepository(), new PostBodyTemplate());

        $handler = new DraftXmlRpcHandler($useCase, $payloadFactory);
        add_filter('xmlrpc_methods', [$handler, 'registerMethod']);
    }
}
