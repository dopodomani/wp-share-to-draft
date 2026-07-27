<?php

declare(strict_types=1);

namespace MaterialCapture\Application;

/**
 * Single source of truth for the category name, so Plugin::activate() and
 * CreateDraftService never risk drifting apart on the literal string.
 */
final class MaterialCategory
{
    public const NAME = '素材候補';

    private function __construct()
    {
    }
}
