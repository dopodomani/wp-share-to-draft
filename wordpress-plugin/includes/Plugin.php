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
}
