<?php

/**
 * Plugin Name:       Material Capture
 * Plugin URI:        https://github.com/dopodomani/wp-share-to-draft
 * Description:       Creates an [INBOX]-prefixed draft post from a shared item via a REST API endpoint (material-capture/v1).
 * Version:           0.1.1
 * Requires at least: 6.0
 * Requires PHP:      8.1
 * Author:            dopodomani
 * License:           MIT
 * License URI:       https://opensource.org/licenses/MIT
 * Text Domain:       material-capture
 *
 * @package MaterialCapture
 */

declare(strict_types=1);

if (!defined('ABSPATH')) {
    exit; // Disallow direct access.
}

require_once __DIR__ . '/vendor/autoload.php';

use MaterialCapture\Plugin;

register_activation_hook(__FILE__, [Plugin::class, 'activate']);
register_deactivation_hook(__FILE__, [Plugin::class, 'deactivate']);

add_action('rest_api_init', static function (): void {
    (new Plugin())->registerRoutes();
});

// No wrapping action here on purpose -- WordPress core has no `xmlrpc_init` action.
// `xmlrpc_methods` is a plain filter, only ever applied when xmlrpc.php itself constructs
// its server, so registering it unconditionally at plugin load time is the correct,
// standard pattern (this is how core plugins like Jetpack add XML-RPC methods too).
(new Plugin())->registerXmlRpcMethods();
