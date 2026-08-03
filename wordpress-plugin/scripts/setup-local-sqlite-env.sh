#!/usr/bin/env bash
# Sets up a local WordPress instance for material-capture development/testing without any
# production site, Docker, or MySQL -- WordPress core + the official "SQLite Database
# Integration" feature plugin, served via PHP's built-in web server. See
# docs/development.md#local-wordpress-test-environment-sqlite-no-docker for the full writeup
# of why this exists and its known limitations.
#
# Idempotent: re-running skips steps whose output already exists (downloads, wp-config.php,
# the installed site). Safe to re-run after pulling plugin code changes -- the plugin
# directory is symlinked, not copied, so changes are picked up immediately.
#
# Usage: ./setup-local-sqlite-env.sh [target-dir] [port]
#   target-dir defaults to a sibling of this repo: ../../../local-wordpress
#   port defaults to 8080

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PLUGIN_DIR/.." && pwd)"

TARGET_DIR="${1:-$(cd "$REPO_ROOT/.." && pwd)/local-wordpress}"
PORT="${2:-8080}"
SITE_DIR="$TARGET_DIR/site"
BASE_URL="http://localhost:$PORT"

ADMIN_USER="admin"
ADMIN_PASSWORD="admin-local-test-only"
ADMIN_EMAIL="admin@localhost.test"

echo "== material-capture local SQLite test environment =="
echo "Target directory: $TARGET_DIR"
echo "Site URL:         $BASE_URL"
echo

mkdir -p "$TARGET_DIR"
cd "$TARGET_DIR"

if [ ! -f wp-cli.phar ]; then
    echo "-- Downloading wp-cli --"
    curl -sSL -o wp-cli.phar https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar
fi

if [ ! -d "$SITE_DIR" ]; then
    echo "-- Downloading WordPress core --"
    curl -sSL -o wordpress-latest.zip https://wordpress.org/latest.zip
    unzip -q wordpress-latest.zip
    mv wordpress site
    rm wordpress-latest.zip
fi

if [ ! -d "$SITE_DIR/wp-content/plugins/sqlite-database-integration" ]; then
    echo "-- Downloading SQLite Database Integration plugin --"
    curl -sSL -o sqlite-database-integration.zip \
        https://downloads.wordpress.org/plugin/sqlite-database-integration.latest-stable.zip
    unzip -q sqlite-database-integration.zip -d "$SITE_DIR/wp-content/plugins/"
    rm sqlite-database-integration.zip
fi

if [ ! -f "$SITE_DIR/wp-content/db.php" ]; then
    echo "-- Wiring the SQLite drop-in --"
    cp "$SITE_DIR/wp-content/plugins/sqlite-database-integration/db.copy" "$SITE_DIR/wp-content/db.php"
fi

if [ ! -f "$SITE_DIR/wp-content/mu-plugins/local-dev-force-https.php" ]; then
    echo "-- Adding the local-only HTTPS spoof (material-capture requires HTTPS; php -S has no TLS) --"
    mkdir -p "$SITE_DIR/wp-content/mu-plugins"
    cat > "$SITE_DIR/wp-content/mu-plugins/local-dev-force-https.php" <<'PHP'
<?php
/**
 * Local dev only: this SQLite test site runs on plain HTTP via `php -S`, which has no TLS
 * support. material-capture correctly rejects non-HTTPS requests (see docs/security.md) --
 * that check is real and should not be weakened in the plugin itself. This mu-plugin spoofs
 * is_ssl() for this local instance only, so the HTTPS requirement can still be exercised
 * against a real (if faked) "https" condition without needing a TLS-terminating proxy.
 */
$_SERVER['HTTPS'] = 'on';
PHP
fi

cd "$SITE_DIR"

if [ ! -f wp-config.php ]; then
    echo "-- Generating wp-config.php (DB credentials are placeholders -- SQLite drop-in ignores them) --"
    php ../wp-cli.phar config create \
        --dbname=unused_sqlite_placeholder \
        --dbuser=unused \
        --dbpass=unused \
        --dbhost=localhost \
        --skip-check \
        --path=.
fi

if ! php ../wp-cli.phar core is-installed --path=. 2>/dev/null; then
    echo "-- Installing WordPress --"
    php ../wp-cli.phar core install \
        --url="$BASE_URL" \
        --title="Material Capture Local Test" \
        --admin_user="$ADMIN_USER" \
        --admin_password="$ADMIN_PASSWORD" \
        --admin_email="$ADMIN_EMAIL" \
        --skip-email \
        --path=.
fi

if [ ! -e wp-content/plugins/material-capture ]; then
    echo "-- Symlinking the plugin from this repo (so local code changes apply immediately) --"
    ln -s "$PLUGIN_DIR" wp-content/plugins/material-capture
fi

php ../wp-cli.phar plugin activate material-capture --path=. >/dev/null

echo
echo "== Ready =="
echo "Start the server with:"
echo "  cd \"$SITE_DIR\" && php -S localhost:$PORT"
echo
echo "Admin login: $ADMIN_USER / $ADMIN_PASSWORD  ($BASE_URL/wp-login.php)"
echo
echo "Create an Application Password for API testing with:"
echo "  cd \"$SITE_DIR\" && php ../wp-cli.phar user application-password create $ADMIN_USER \"material-capture-test\" --porcelain"
echo
echo "REST needs the ?rest_route= form against the built-in server (no pretty permalinks):"
echo "  curl -X POST \"$BASE_URL/?rest_route=/material-capture/v1/draft\" -u \"$ADMIN_USER:<app-password>\" -H 'Content-Type: application/json' -d '{\"title\":\"Test\"}'"
echo
echo "XML-RPC: $BASE_URL/xmlrpc.php"
