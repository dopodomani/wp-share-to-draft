<?php

declare(strict_types=1);

namespace MaterialCapture\Tests\Application;

use DateTimeImmutable;
use MaterialCapture\Application\CreateDraftService;
use MaterialCapture\Application\MaterialCategory;
use MaterialCapture\Application\PostBodyRendererInterface;
use MaterialCapture\Application\PostRepositoryInterface;
use MaterialCapture\Domain\DraftPayload;
use MaterialCapture\Domain\Exceptions\CategoryUnavailableException;
use Mockery;
use Mockery\Adapter\Phpunit\MockeryPHPUnitIntegration;
use PHPUnit\Framework\TestCase;

final class CreateDraftServiceTest extends TestCase
{
    use MockeryPHPUnitIntegration;

    private const CATEGORY_ID = 7;
    private const AUTHOR_ID = 42;

    public function test_title_is_prefixed_with_inbox_once(): void
    {
        $payload = $this->payload('半導体市況、AI需要で最高値更新');
        $now = new DateTimeImmutable('2026-07-27T09:15:03+09:00');

        $posts = $this->postsMock(insertedTitle: '[INBOX] 半導体市況、AI需要で最高値更新');
        $renderer = $this->rendererMock($payload, $now);

        $result = (new CreateDraftService($posts, $renderer))->create($payload, self::AUTHOR_ID, $now);

        self::assertSame('[INBOX] 半導体市況、AI需要で最高値更新', $result->title);
    }

    public function test_already_prefixed_title_is_not_double_prefixed(): void
    {
        $payload = $this->payload('[INBOX] Already Prefixed');
        $now = new DateTimeImmutable();

        $posts = $this->postsMock(insertedTitle: '[INBOX] Already Prefixed');
        $renderer = $this->rendererMock($payload, $now);

        $result = (new CreateDraftService($posts, $renderer))->create($payload, self::AUTHOR_ID, $now);

        self::assertSame('[INBOX] Already Prefixed', $result->title);
    }

    public function test_missing_category_throws_category_unavailable(): void
    {
        $payload = $this->payload('Title');

        $posts = Mockery::mock(PostRepositoryInterface::class);
        $posts->shouldReceive('resolveConfiguredCategoryId')->once()->andReturn(null);
        $posts->shouldNotReceive('insertDraft');

        $renderer = Mockery::mock(PostBodyRendererInterface::class);

        $this->expectException(CategoryUnavailableException::class);

        (new CreateDraftService($posts, $renderer))->create($payload, self::AUTHOR_ID);
    }

    public function test_author_id_is_passed_through_to_insert_draft(): void
    {
        $payload = $this->payload('Title');
        $now = new DateTimeImmutable();

        $posts = Mockery::mock(PostRepositoryInterface::class);
        $posts->shouldReceive('resolveConfiguredCategoryId')->once()->andReturn(self::CATEGORY_ID);
        $posts->shouldReceive('insertDraft')
            ->once()
            ->with('[INBOX] Title', Mockery::type('string'), self::CATEGORY_ID, self::AUTHOR_ID)
            ->andReturn(123);
        $posts->shouldReceive('editLink')->with(123)->andReturn('https://example.com/edit');
        $posts->shouldReceive('previewLink')->with(123)->andReturn('https://example.com/preview');

        $renderer = $this->rendererMock($payload, $now);

        $result = (new CreateDraftService($posts, $renderer))->create($payload, self::AUTHOR_ID, $now);

        self::assertSame(123, $result->postId);
        self::assertSame('draft', $result->status);
        self::assertSame('https://example.com/edit', $result->editUrl);
        self::assertSame('https://example.com/preview', $result->previewUrl);
        self::assertSame(MaterialCategory::NAME, $result->category);
        self::assertSame($now, $result->createdAt);
    }

    private function payload(string $title): DraftPayload
    {
        return DraftPayload::create($title, 'https://example.com', null, null, 'unknown', null);
    }

    private function postsMock(string $insertedTitle): PostRepositoryInterface
    {
        $posts = Mockery::mock(PostRepositoryInterface::class);
        $posts->shouldReceive('resolveConfiguredCategoryId')->once()->andReturn(self::CATEGORY_ID);
        $posts->shouldReceive('insertDraft')
            ->once()
            ->with($insertedTitle, Mockery::type('string'), self::CATEGORY_ID, self::AUTHOR_ID)
            ->andReturn(1);
        $posts->shouldReceive('editLink')->andReturn(null);
        $posts->shouldReceive('previewLink')->andReturn(null);

        return $posts;
    }

    private function rendererMock(DraftPayload $payload, DateTimeImmutable $now): PostBodyRendererInterface
    {
        $renderer = Mockery::mock(PostBodyRendererInterface::class);
        $renderer->shouldReceive('render')->once()->with($payload, $now)->andReturn('rendered body');

        return $renderer;
    }
}
