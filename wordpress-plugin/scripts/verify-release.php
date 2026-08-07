<?php

declare(strict_types=1);

if ($argc !== 2) {
    fwrite(STDERR, "Usage: php verify-release.php <extracted-plugin-directory>\n");
    exit(2);
}

$pluginDirectory = realpath($argv[1]);
if ($pluginDirectory === false) {
    fwrite(STDERR, "Plugin directory does not exist.\n");
    exit(1);
}

$bootstrap = $pluginDirectory . DIRECTORY_SEPARATOR . 'material-capture.php';
$autoload = $pluginDirectory . DIRECTORY_SEPARATOR . 'vendor' . DIRECTORY_SEPARATOR . 'autoload.php';
if (!is_file($bootstrap) || !is_file($autoload)) {
    fwrite(STDERR, "Release is missing the plugin bootstrap or Composer autoloader.\n");
    exit(1);
}

define('ABSPATH', $pluginDirectory . DIRECTORY_SEPARATOR);

function register_activation_hook(string $file, callable $callback): void
{
}

function register_deactivation_hook(string $file, callable $callback): void
{
}

function add_action(string $hookName, callable $callback): void
{
}

function add_filter(string $hookName, callable $callback): void
{
}

require $bootstrap;

if (!class_exists(MaterialCapture\Plugin::class)) {
    fwrite(STDERR, "Plugin class was not autoloaded.\n");
    exit(1);
}

fwrite(STDOUT, "Release plugin loaded successfully.\n");
