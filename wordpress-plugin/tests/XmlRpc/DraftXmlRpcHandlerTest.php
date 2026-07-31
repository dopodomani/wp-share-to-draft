<?php

declare(strict_types=1);

namespace MaterialCapture\Tests\XmlRpc;

use Brain\Monkey\Functions;
use DateTimeImmutable;
use IXR_Error;
use MaterialCapture\Application\CreateDraftUseCase;
use MaterialCapture\Application\DraftPayloadFactory;
use MaterialCapture\Application\InputSanitizerInterface;
use MaterialCapture\Application\MaterialCategory;
use MaterialCapture\Domain\DraftPayload;
use MaterialCapture\Domain\DraftResult;
use MaterialCapture\Domain\Exceptions\CategoryUnavailableException;
use MaterialCapture\Domain\Exceptions\DraftCreationFailedException;
use MaterialCapture\Tests\BrainMonkeyTestCase;
use MaterialCapture\XmlRpc\DraftXmlRpcHandler;
use Mockery;
use wp_xmlrpc_server;

/**
 * Mirrors DraftControllerTest's structure and philosophy -- see
 * docs/phase2c-xmlrpc-design.md#test-plan. DraftPayloadFactory is never mocked (deliberately
 * concrete/final); only CreateDraftUseCase (an interface) and wp_xmlrpc_server (WordPress
 * core's own class, stubbed in tests/wp-stubs.php) are Mockery targets.
 *
 * createDraft() takes a single $args parameter -- WordPress's real XML-RPC dispatcher never
 * passes a server instance as a second argument, it's reached via the $wp_xmlrpc_server
 * global instead (see the handler's own docblock for why). Each test sets that global before
 * calling createDraft() and unsets it afterward so tests don't leak state into each other.
 */
final class DraftXmlRpcHandlerTest extends BrainMonkeyTestCase
{
    protected function tearDown(): void
    {
        unset($GLOBALS['wp_xmlrpc_server']);
        parent::tearDown();
    }

    public function test_login_failure_returns_the_servers_error_unchanged(): void
    {
        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldNotReceive('create');

        $server = $this->registerGlobalServer();
        $server->shouldReceive('login')->once()->with('user', 'wrong-password')->andReturn(false);
        $server->error = new IXR_Error(403, 'Incorrect username or password.');

        $handler = new DraftXmlRpcHandler($useCase, $this->passthroughFactory());
        $result = $handler->createDraft(['user', 'wrong-password', 'Title', 'https://example.com']);

        self::assertSame($server->error, $result);
    }

    public function test_plain_http_maps_to_a_400_fault(): void
    {
        Functions\when('is_ssl')->justReturn(false);
        $this->registerLoggedInGlobalServer();

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldNotReceive('create');

        $handler = new DraftXmlRpcHandler($useCase, $this->passthroughFactory());
        $result = $handler->createDraft(['user', 'app-password', 'Title', 'https://example.com']);

        self::assertInstanceOf(IXR_Error::class, $result);
        self::assertSame(400, $result->code);
    }

    public function test_missing_edit_posts_capability_maps_to_a_403_fault(): void
    {
        Functions\when('is_ssl')->justReturn(true);
        Functions\when('current_user_can')->justReturn(false);
        $this->registerLoggedInGlobalServer();

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldNotReceive('create');

        $handler = new DraftXmlRpcHandler($useCase, $this->passthroughFactory());
        $result = $handler->createDraft(['user', 'app-password', 'Title', 'https://example.com']);

        self::assertInstanceOf(IXR_Error::class, $result);
        self::assertSame(403, $result->code);
    }

    public function test_invalid_payload_maps_to_a_400_fault(): void
    {
        Functions\when('is_ssl')->justReturn(true);
        Functions\when('current_user_can')->justReturn(true);
        $this->registerLoggedInGlobalServer();

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldNotReceive('create');

        $handler = new DraftXmlRpcHandler($useCase, $this->passthroughFactory());
        // Missing url -> the real DraftPayloadFactory/DraftPayload rejects it for real.
        $result = $handler->createDraft(['user', 'app-password', 'Title', '']);

        self::assertInstanceOf(IXR_Error::class, $result);
        self::assertSame(400, $result->code);
    }

    public function test_category_unavailable_maps_to_a_409_fault(): void
    {
        Functions\when('is_ssl')->justReturn(true);
        Functions\when('current_user_can')->justReturn(true);
        Functions\when('get_current_user_id')->justReturn(42);
        $this->registerLoggedInGlobalServer();

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldReceive('create')->once()->andThrow(new CategoryUnavailableException());

        $handler = new DraftXmlRpcHandler($useCase, $this->passthroughFactory());
        $result = $handler->createDraft(['user', 'app-password', 'Title', 'https://example.com']);

        self::assertInstanceOf(IXR_Error::class, $result);
        self::assertSame(409, $result->code);
    }

    public function test_creation_failure_maps_to_a_500_fault(): void
    {
        Functions\when('is_ssl')->justReturn(true);
        Functions\when('current_user_can')->justReturn(true);
        Functions\when('get_current_user_id')->justReturn(42);
        $this->registerLoggedInGlobalServer();

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldReceive('create')->once()->andThrow(new DraftCreationFailedException('db error'));

        $handler = new DraftXmlRpcHandler($useCase, $this->passthroughFactory());
        $result = $handler->createDraft(['user', 'app-password', 'Title', 'https://example.com']);

        self::assertInstanceOf(IXR_Error::class, $result);
        self::assertSame(500, $result->code);
    }

    public function test_successful_case_returns_a_struct_with_all_seven_fields(): void
    {
        Functions\when('is_ssl')->justReturn(true);
        Functions\when('current_user_can')->justReturn(true);
        Functions\when('get_current_user_id')->justReturn(42);
        $this->registerLoggedInGlobalServer();

        $now = new DateTimeImmutable('2026-07-30T09:15:03Z');
        $result = new DraftResult(1, 'draft', '[INBOX] Title', 'https://x/edit', 'https://x/preview', MaterialCategory::NAME, $now);

        $useCase = Mockery::mock(CreateDraftUseCase::class);
        $useCase->shouldReceive('create')->once()->with(Mockery::type(DraftPayload::class), 42)->andReturn($result);

        $handler = new DraftXmlRpcHandler($useCase, $this->passthroughFactory());
        $response = $handler->createDraft(['user', 'app-password', 'Title', 'https://example.com']);

        self::assertIsArray($response);
        self::assertSame(1, $response['post_id']);
        self::assertSame('draft', $response['status']);
        self::assertSame('[INBOX] Title', $response['title']);
        self::assertSame('https://x/edit', $response['edit_url']);
        self::assertSame('https://x/preview', $response['preview_url']);
        self::assertSame(MaterialCategory::NAME, $response['category']);
        self::assertSame($now->format(DATE_ATOM), $response['created_at']);
    }

    private function registerLoggedInGlobalServer(): wp_xmlrpc_server
    {
        $server = $this->registerGlobalServer();
        $server->shouldReceive('login')->andReturn(true);

        return $server;
    }

    private function registerGlobalServer(): wp_xmlrpc_server
    {
        $server = Mockery::mock(wp_xmlrpc_server::class);
        $GLOBALS['wp_xmlrpc_server'] = $server;

        return $server;
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
