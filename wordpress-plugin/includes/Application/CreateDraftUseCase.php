<?php

declare(strict_types=1);

namespace MaterialCapture\Application;

use DateTimeImmutable;
use MaterialCapture\Domain\DraftPayload;
use MaterialCapture\Domain\DraftResult;
use MaterialCapture\Domain\Exceptions\CategoryUnavailableException;
use MaterialCapture\Domain\Exceptions\DraftCreationFailedException;

/**
 * Rest\DraftController depends on this interface, never on CreateDraftService directly —
 * that's what lets DraftControllerTest mock the use case without fighting a `final` class.
 */
interface CreateDraftUseCase
{
    /**
     * Creates an [INBOX] draft post from a validated payload.
     *
     * @throws CategoryUnavailableException
     * @throws DraftCreationFailedException
     */
    public function create(DraftPayload $payload, int $authorId, ?DateTimeImmutable $now = null): DraftResult;
}
