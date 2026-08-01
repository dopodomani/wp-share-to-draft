<?php

declare(strict_types=1);

namespace MaterialCapture\Tests\Rest;

use Brain\Monkey\Functions;
use DateTimeImmutable;
use MaterialCapture\Application\CreateDraftUseCase;
use MaterialCapture\Application\DraftPayloadFactory;
use MaterialCapture\Application\InputSanitizerInterface;
use MaterialCapture\Application\MaterialCategory;
use MaterialCapture\Domain\DraftPayload;
use MaterialCapture\Domain\DraftResult;
use MaterialCapture\Domain\Exceptions\CategoryUnavailableException;
use MaterialCapture\Domain\Exceptions\DraftCreationFailedException;
use MaterialCapture\Rest\DraftController;
use MaterialCapture\Rest\RestResponseFactory;
use MaterialCapture\Tests\BrainMonkeyTestCase;
use Mockery;
use WP_Error;
use WP_REST_Request;

/**
 * DraftPayloadFactory is a deliberately concrete (final) class — see
 * docs/phase2-wordpress-plugin-design.md, "only interfaces are Mockery targets." So this
 * suite never mocks it: it's constructed for real with a mocked InputSanitizerInterface
 * that passes values through unchanged, and exercised via real request payloads. Only
 * CreateDraftUseCase (an interface) is mocked.
 */
final class DraftControllerTest extends BrainMonkeyTestCase
{
    public function test_permission_callback_denies_over_plain_http(): void
    {
        Functions\when('is_ssl')->justReturn(false);

        $result = $this->controller()->permission_callback(new WP_REST_Request());

        self::assertInstanceOf(WP_Error::class, $result);
        self::assertSame('https_required', $result->get_error_code());
        self::assertSame(400, $result->get_error_data()['status']);
    }

    public function test_permission_callback_denies_without_edit_posts_capability(): void
    {
        Functions\when('is_ssl')->justReturn(true);
        Functions\when('current_user_can')->justReturn(false);
        Functions\when('is_user_logged_in')->justReturn(true);

        $result = $this->controller()->permission_callback(new WP_REST_Request());

        self::assertInstanceOf(WP_Error::class, $result);
        self::assertSame('insufficient_capability', $result->get_error_code());
        self::assertSame(403, $result->get_error_data()['status']);
    }

    public function test_permission_callback_denies_anonymous_user_with_401(): void
    {
        Functions\when('is_ssl')->justReturn(true);
        Functions\when('current_user_can')->justReturn(false);
        Functions\when('is_user_logged_in')->justReturn(false);

        $result = $this->controller()->permission_callback(new WP_REST_Request());

        self::assertInstanceOf(WP_Error::class, $result);
        self::assertSame('insufficient_capability', $result->get_error_code());
        self::assertSame(401, $result->get_error_data()['status']);
    }

    public function test_permission_callback_allows_https_and_capable_user(): void
    {
        Functions\when('is_ssl')->justReturn(true);
        Functions\when('current_user_can')->justReturn(true);

        self::assertTrue($this->controller()->permission_callback(new WP_REST_Request()));
    }

    public function test_create_draft_returns_201_on_success(): void
    {
        Functions\when('get_current_user_id')->justReturn(42);

        $now = new DateTimeImmutable();
        $result = new DraftResult(1, 'draft', '[INBOX] Title', null, null, MaterialCategory::NAME, $now);

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldReceive('create')
            ->once()
            ->with(Mockery::type(DraftPayload::class), 42)
            ->andReturn($result);

        $controller = new DraftController($useCase, $this->passthroughFactory(), new RestResponseFactory());
        $response = $controller->create_draft(
            new WP_REST_Request(['title' => 'Title', 'url' => 'https://example.com'])
        );

        self::assertSame(201, $response->get_status());
        self::assertSame(1, $response->get_data()['post_id']);
    }

    public function test_create_draft_maps_invalid_payload_to_400(): void
    {
        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldNotReceive('create');

        $controller = new DraftController($useCase, $this->passthroughFactory(), new RestResponseFactory());
        // Missing title -> the real DraftPayloadFactory/DraftPayload rejects it for real,
        // no exception mocking required. (url is optional, so an absent url alone would not
        // trigger this -- see docs/tech-decisions.md#12-url-is-optional.)
        $response = $controller->create_draft(new WP_REST_Request([]));

        self::assertInstanceOf(WP_Error::class, $response);
        self::assertSame('missing_required_field', $response->get_error_code());
        self::assertSame(400, $response->get_error_data()['status']);
    }

    public function test_create_draft_maps_category_unavailable_to_409(): void
    {
        Functions\when('get_current_user_id')->justReturn(42);

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldReceive('create')->once()->andThrow(new CategoryUnavailableException());

        $controller = new DraftController($useCase, $this->passthroughFactory(), new RestResponseFactory());
        $response = $controller->create_draft(
            new WP_REST_Request(['title' => 'Title', 'url' => 'https://example.com'])
        );

        self::assertInstanceOf(WP_Error::class, $response);
        self::assertSame('category_unavailable', $response->get_error_code());
        self::assertSame(409, $response->get_error_data()['status']);
    }

    public function test_create_draft_maps_creation_failure_to_500(): void
    {
        Functions\when('get_current_user_id')->justReturn(42);

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldReceive('create')->once()->andThrow(new DraftCreationFailedException('db error'));

        $controller = new DraftController($useCase, $this->passthroughFactory(), new RestResponseFactory());
        $response = $controller->create_draft(
            new WP_REST_Request(['title' => 'Title', 'url' => 'https://example.com'])
        );

        self::assertInstanceOf(WP_Error::class, $response);
        self::assertSame('insert_failed', $response->get_error_code());
        self::assertSame(500, $response->get_error_data()['status']);
    }

    private function controller(): DraftController
    {
        return new DraftController(
            Mockery::mock(CreateDraftUseCase::class),
            $this->passthroughFactory(),
            new RestResponseFactory()
        );
    }

    private function passthroughFactory(): DraftPayloadFactory
    {
        $sanitizer = Mockery::mock(InputSanitizerInterface::class);
        $sanitizer->shouldReceive('sanitizeTitle')->andReturnUsing(static fn (string $v) => $v);
        $sanitizer->shouldReceive('sanitizeUrl')->andReturnUsing(static fn (string $v) => $v);
        $sanitizer->shouldReceive('sanitizeSharedText')->andReturnUsing(static fn (?string $v) => $v);
        $sanitizer->shouldReceive('sanitizeMemo')->andReturnUsing(static fn (?string $v) => $v);
        $sanitizer->shouldReceive('sanitizeSource')->andReturn('unknown');

        return new DraftPayloadFactory($sanitizer);
    }
}
