<?php

/**
 * Uninstall handler.
 *
 * Removes ONLY this plugin's own options. Never deletes the material category or any
 * posts, regardless of whether the category is empty or was created by this plugin —
 * see docs/phase2-wordpress-plugin-design.md#category-lifecycle for the rationale.
 *
 * @package MaterialCapture
 */

declare(strict_types=1);

if (!defined('WP_UNINSTALL_PLUGIN')) {
    exit;
}

delete_option('material_capture_category_id');
