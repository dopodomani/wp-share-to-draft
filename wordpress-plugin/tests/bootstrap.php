<?php

/**
 * Loads Composer autoload, PHPUnit bootstrap, Brain\Monkey and Mockery setup for the
 * material-capture unit suite. No WordPress install is loaded — see wp-stubs.php for
 * the minimal class stand-ins required for type declarations, and
 * docs/phase2-wordpress-plugin-design.md for what's deliberately deferred to Phase 4
 * integration testing instead.
 */

declare(strict_types=1);

require_once dirname(__DIR__) . '/vendor/autoload.php';
require_once __DIR__ . '/wp-stubs.php';
