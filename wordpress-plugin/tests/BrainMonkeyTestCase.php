<?php

declare(strict_types=1);

namespace MaterialCapture\Tests;

use Brain\Monkey;
use Mockery\Adapter\Phpunit\MockeryPHPUnitIntegration;
use PHPUnit\Framework\TestCase;

/**
 * Base class for tests that stub WordPress functions/hooks via Brain\Monkey, used
 * alongside Mockery for this plugin's own object collaborators.
 */
abstract class BrainMonkeyTestCase extends TestCase
{
    use MockeryPHPUnitIntegration;

    protected function setUp(): void
    {
        parent::setUp();
        Monkey\setUp();
    }

    protected function tearDown(): void
    {
        Monkey\tearDown();
        parent::tearDown();
    }
}
