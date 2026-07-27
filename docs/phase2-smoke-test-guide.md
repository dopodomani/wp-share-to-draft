# Phase 2b Smoke Test Guide

**Purpose:** manually verify the `material-capture` plugin against a real WordPress instance, to close out the one remaining Phase 2b Definition-of-Done item (see [ROADMAP.md](../ROADMAP.md#phase-2b--implementation-current)). This is a **procedure document, not a design change** — no plugin code is modified as part of this guide.

No code is written here. Record actual results in [docs/phase2-smoke-test-results.md](phase2-smoke-test-results.md), not in this file — this file is the reusable procedure; that one is the dated record of a specific run.

## Why two environments

This plugin enforces HTTPS (`400 https_required` — see [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md#authentication--authorization-division-of-responsibility)), and the checklist requires verifying *both* the HTTPS happy path *and* the plain-HTTP rejection. One local environment rarely gives you both cleanly, so this guide uses:

- **LocalWP, with SSL enabled** — for every HTTPS scenario (happy path + all the auth/validation error codes).
- **`wp-env` (Docker), served over plain `http://localhost`** — for the one `https_required` scenario, since it needs a real non-HTTPS request without fighting a tool that auto-upgrades to HTTPS.

You don't need both if you only care about one side — pick LocalWP if you just want the happy path and most error codes, and skip to [Testing HTTPS enforcement](#testing-https-enforcement-plain-http) only if you also want to exercise `https_required`.

## 1. Test environment setup

### Option A — LocalWP (recommended for most of the checklist)

1. Install [LocalWP](https://localwp.com/) if not already installed (per the project's dev-environment notes).
2. **Add Site** → choose a name (e.g. `material-capture-test`) → **Preferred** environment (PHP 8.1+, matching this plugin's declared minimum — see [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md#plugin-identity)) → create a WordPress admin user, note the username/password.
3. Start the site. Open the site's **Site Shell** or note its **Site Path** (e.g. `C:\Users\<you>\Local Sites\material-capture-test\app\public`).
4. Enable HTTPS: in LocalWP's site overview, click **Trust** (or the SSL toggle) so the site is served over `https://material-capture-test.local` with a locally-trusted certificate. Confirm by opening that URL in a browser with no certificate warning.

### Option B — `wp-env` (Docker) — for the plain-HTTP scenario

Requires Docker Desktop and Node.js (already on the recommended install list).

1. From the repository root:
   ```bash
   npm install -g @wordpress/env
   ```
2. Create `.wp-env.json` at the repository root (temporary, for this smoke test only — not committed, since it's local test tooling, not project source):
   ```json
   {
       "core": "WordPress/WordPress#master",
       "phpVersion": "8.1",
       "mappings": {
           "wp-content/plugins/material-capture": "./wordpress-plugin"
       }
   }
   ```
   The `mappings` key is what lets the plugin appear under the `material-capture` slug in `wp-content/plugins/` even though the source lives at `wordpress-plugin/` (see [Deploying the plugin](#2-deploying-the-plugin-as-material-capture) below — `wp-env` handles this step for you via the mapping, unlike LocalWP).
3. Start it:
   ```bash
   wp-env start
   ```
4. It serves at `http://localhost:8888` (admin at `http://localhost:8888/wp-admin`, default credentials `admin` / `password` unless configured otherwise — check `wp-env` output on start).

## 2. Deploying the plugin as `material-capture/`

Per [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md#release-packaging-source-layout-vs-installed-plugin-folder), the repository keeps source at `wordpress-plugin/`; a real WordPress install needs it under `wp-content/plugins/material-capture/`.

- **`wp-env`**: already handled by the `mappings` entry in `.wp-env.json` above — nothing further to do.
- **LocalWP**: no mapping mechanism, so copy the folder:
  1. Run the Composer packaging step first (next section) so `vendor/` exists.
  2. Copy the entire `wordpress-plugin/` directory into `<LocalWP site path>\app\public\wp-content\plugins\`, then rename the copied folder from `wordpress-plugin` to `material-capture`.
  3. **Do not symlink or copy `tests/`, `phpunit.xml.dist`, `phpcs.xml.dist`, or `composer.json`/`composer.lock` into the live site** — they're harmless if present but unnecessary; a real release zip (Phase 5) excludes them entirely. For this manual smoke test it's fine to leave them in, just don't treat their presence as something to fix.
  4. Re-copy after any code change — there's no live sync in this setup. Acceptable for a one-time smoke test; not a workflow to keep long-term.

## 3. Composer packaging (production dependencies)

The plugin's `composer.json` declares no runtime package dependencies (only `php: >=8.1` — Brain\Monkey/Mockery/PHPUnit/PHPCS are all `require-dev`), so the "production install" step is mainly about **excluding dev tooling** and generating the autoloader:

```bash
cd wordpress-plugin
composer install --no-dev --optimize-autoloader
```

Verify `vendor/autoload.php` exists afterward — that's the only thing `material-capture.php` requires at runtime (see [material-capture.php](../wordpress-plugin/material-capture.php)). If you already ran `composer install` (with dev dependencies) for the unit test suite, either re-run with `--no-dev` first or just leave the dev dependencies in place for this local smoke test — their presence doesn't affect plugin behavior, only the released zip needs to be `--no-dev`-clean (a Phase 5 packaging step, not a Phase 2b smoke-test requirement).

## 4. Activating the plugin

1. In wp-admin → **Plugins**, find **Material Capture** and **Activate**.
2. This runs `Plugin::activate()` — per the design, it creates the `素材候補` category and stores its id in the `material_capture_category_id` option. Confirm in **Posts → Categories** that `素材候補` now exists.
3. If activation errors out, check the site's PHP error log (LocalWP: site's **Logs** tab; `wp-env`: `wp-env logs`) before assuming a plugin bug — a fatal here most often means `vendor/autoload.php` is missing (Composer step skipped).

## 5. Creating an Application Password

1. wp-admin → **Users → Profile** (or **Users → All Users → [user] → Edit** for a non-admin test user — useful for the 403 scenario below).
2. Scroll to **Application Passwords**. Enter a name (e.g. `smoke-test`), click **Add New Application Password**.
3. WordPress shows the password **once**, formatted with spaces (e.g. `abcd 1234 efgh 5678 ijkl 9012`). Copy it now — the spaces are cosmetic and optional; both forms work for Basic Auth.
4. For the `403 insufficient_capability` scenario, repeat this for a second user whose role lacks `edit_posts` (e.g. **Subscriber**).

## 6. API call examples

Replace `SITE_URL`, `USERNAME`, and `APP_PASSWORD` below. Examples assume LocalWP (`https://material-capture-test.local`); swap in the `wp-env` URL (`http://localhost:8888`) for the HTTP-only scenario.

### curl — happy path

```bash
curl -i -u "USERNAME:APP_PASSWORD" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "半導体市況、AI需要で最高値更新",
    "url": "https://www.nikkei.com/article/xxxxx",
    "shared_text": "来期は車載向けが牽引役になるとの分析。",
    "memo": "スモークテスト",
    "source": "smoke_test",
    "shared_at": "2026-07-28T09:15:00+09:00"
  }' \
  https://material-capture-test.local/wp-json/material-capture/v1/draft
```

Expect `HTTP/1.1 201 Created` and a JSON body matching [api-spec.md](api-spec.md#endpoints) (`post_id`, `status: draft`, `title` prefixed `[INBOX] `, `edit_url`, `preview_url`, `category: 素材候補`, `created_at`).

### PowerShell — happy path

Windows PowerShell 5.1 doesn't support `Invoke-RestMethod -Authentication Basic` (that's PowerShell 7+ only), so build the Basic Auth header manually — this form works on both:

```powershell
$username = 'USERNAME'
$appPassword = 'APP_PASSWORD'
$pair = "$($username):$($appPassword)"
$base64 = [System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($pair))
$headers = @{ Authorization = "Basic $base64" }

$body = @{
    title       = '半導体市況、AI需要で最高値更新'
    url         = 'https://www.nikkei.com/article/xxxxx'
    shared_text = '来期は車載向けが牽引役になるとの分析。'
    memo        = 'スモークテスト'
    source      = 'smoke_test'
    shared_at   = '2026-07-28T09:15:00+09:00'
} | ConvertTo-Json -Compress

# Explicit UTF-8 byte encoding avoids mojibake for Japanese text — Invoke-RestMethod's
# default string-body encoding is not reliably UTF-8 on Windows PowerShell 5.1.
Invoke-RestMethod -Uri 'https://material-capture-test.local/wp-json/material-capture/v1/draft' `
    -Method Post -Headers $headers `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

(On PowerShell 7+, `-Authentication Basic -Credential (Get-Credential)` is a shorter equivalent if you prefer it.)

### Error-path variants

Reuse the same `$headers`/curl `-u` pattern; only the body or auth changes:

| Scenario | How to trigger |
|---|---|
| `401` (no/invalid Application Password) | Omit `-u`/`$headers` entirely, or use a wrong password |
| `403 insufficient_capability` | Use the Subscriber user's Application Password from step 5 |
| `400 invalid_url` | Send `"url": "not-a-url"` |
| `400 missing_required_field` | Omit `title` (or `url`) from the body entirely |
| `400 invalid_shared_at` | Send `"shared_at": "2026-07-28"` (no offset — see [api-spec.md](api-spec.md#endpoints)) |
| `409 category_unavailable` | In wp-admin, delete the `素材候補` category, then repeat the happy-path request |
| `400 https_required` | See [next section](#testing-https-enforcement-plain-http) |

## Testing HTTPS enforcement (plain HTTP)

Use the `wp-env` environment (plain `http://localhost:8888`) for this one, so there's no tool-level HTTPS upgrade in the way:

```bash
curl -i -u "USERNAME:APP_PASSWORD" \
  -H "Content-Type: application/json" \
  -d '{"title": "Title", "url": "https://example.com"}' \
  http://localhost:8888/wp-json/material-capture/v1/draft
```

Expect `HTTP/1.1 400` with `{"code":"https_required", ...}` — confirming `is_ssl()` correctly reports `false` here and the plugin's own precondition check (not WordPress core) is what rejected the request.

## 7. Smoke test checklist

Record pass/fail for each in [docs/phase2-smoke-test-results.md](phase2-smoke-test-results.md), not here.

**Setup / activation**
- [ ] Plugin activates with no fatal error or warning
- [ ] `素材候補` category exists after activation
- [ ] The REST route is registered (`GET /wp-json/material-capture/v1` lists the `draft` route, or a request to an unknown method on it returns a routing-level error rather than 404-unregistered)

**Happy path (`201`)**
- [ ] Correct Application Password → `201 Created`
- [ ] Created post's title is prefixed `[INBOX] `
- [ ] Created post's status is `draft`
- [ ] Created post's author is the authenticated user (check in wp-admin, not just the response)
- [ ] Created post is assigned to `素材候補`
- [ ] Post body's "保存日時" is the server's own time (not `shared_at`)
- [ ] Post body's "共有日時" matches the `shared_at` sent in the request
- [ ] Post body's 元URL, 共有元, メモ all match what was sent
- [ ] Response includes non-null `edit_url` and `preview_url`, and both actually open the post in wp-admin

**Error paths**
- [ ] No/invalid Application Password → `401` (WordPress's own error shape, not a `material-capture` body — see [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md#authentication--authorization-division-of-responsibility))
- [ ] Authenticated user lacking `edit_posts` → `403 insufficient_capability`
- [ ] Malformed `url` → `400 invalid_url`
- [ ] Missing `title` or `url` → `400 missing_required_field`
- [ ] `素材候補` deleted from wp-admin, then request repeated → `409 category_unavailable`
- [ ] Plain HTTP request (via `wp-env`) → `400 https_required`

## 8. Recording results

Copy [docs/phase2-smoke-test-results.md](phase2-smoke-test-results.md)'s template section (or just fill in the existing one) with the date, environment used, and pass/fail + notes for each checklist item above. Per [ROADMAP.md](../ROADMAP.md#phase-2b--implementation-current), Phase 2b is only complete once every item there is checked off.
