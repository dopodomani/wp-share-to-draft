# API Specification — `material-capture/v1`

REST namespace exposed by the `wordpress-plugin`. Versioned independently of the plugin's own release version — breaking changes bump `v1` → `v2`, additive changes do not.

Base path: `https://<your-site>/wp-json/material-capture/v1`

## Authentication

**WordPress Application Passwords** (built into WordPress core since 5.6), sent as HTTP Basic Auth over **HTTPS only**.

```
Authorization: Basic base64(username:application_password)
```

- No custom token system to build or maintain.
- Revocable per-device from wp-admin (Users → Profile → Application Passwords) without touching the user's login password.
- Requests over plain HTTP are rejected by the plugin regardless of server config (defense in depth — see [security.md](security.md)).

Full comparison against JWT / OAuth1 / custom API keys is in [security.md](security.md#authentication-method-comparison).

**Who handles what, precisely** (revised after design review — see [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md#authentication--authorization-division-of-responsibility)):

- Missing or invalid Application Password credentials never reach plugin code — WordPress core's own Application Password authentication and REST framework handle this and return **WordPress's own standard REST error shape** (its own `code`, e.g. `rest_not_logged_in`), not a `material-capture`-specific body. Treat this as "some `401` in WordPress's standard shape," not a contract this plugin defines.
- Authenticated but missing the `edit_posts` capability, and the HTTPS requirement, **are** plugin-defined — see the error table below.

## Endpoints

### `POST /draft`

Creates a WordPress draft post from a shared item.

**Request headers**

| Header | Required | Value |
|---|---|---|
| `Authorization` | Yes | `Basic <base64>` |
| `Content-Type` | Yes | `application/json` |

**Request body**

```json
{
  "title": "半導体市況、AI需要で最高値更新",
  "url": "https://www.nikkei.com/article/xxxxx",
  "shared_text": "来期は車載向けが牽引役になるとの分析。",
  "memo": "車載半導体の記事と合わせて読む",
  "source": "chrome-share",
  "shared_at": "2026-07-27T09:15:00+09:00"
}
```

| Field | Type | Required | Max length (via `mb_strlen`) | Notes |
|---|---|---|---|---|
| `title` | string | Yes | 300 chars | Original page title. Plugin prefixes it with `[INBOX] ` server-side, **idempotently** — if the client's title already starts with `[INBOX] ` (e.g. a retried submission), it is not prefixed twice. |
| `url` | string (URL) | No | 2,048 chars | If present, must be an absolute `http`/`https` URL; validated with `esc_url_raw` server-side plus a plain-PHP format check in the domain layer (see [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md)). Optional since v1.1 — see [docs/tech-decisions.md #12](tech-decisions.md#12-url-is-optional) for why: Chrome's "share selected text" action doesn't reliably include the source page's URL at all. |
| `shared_text` | string | No | 50,000 chars | Raw text captured from the Android `ACTION_SEND` extra, if the source app included any (e.g. a highlighted excerpt). |
| `memo` | string | No | 10,000 chars | User-entered note from the confirmation screen. |
| `source` | string | No | 64 chars | **Free-form** lowercase identifier, sanitized via `sanitize_key` (`[a-z0-9_-]`, lowercased) — not a fixed allowlist, so future capture sources (PWA, webhook, other clients) don't require a plugin update to identify themselves. Empty or fully-stripped input defaults to `unknown`. Used for the "共有元" field. |
| `shared_at` | string (ISO 8601) | No | — | Client-reported share time. **Not** used for "保存日時" (see [Post creation semantics](#post-creation-semantics) — that's always the server's own creation time). Must match `YYYY-MM-DDTHH:mm:ss[.ffffff](Z\|±HH:mm)` — a fixed-offset or `Z`-suffixed RFC 3339 timestamp; ambiguous or offset-less values are rejected (`400 invalid_shared_at`), not guessed at. |

There is no application-level total-body-size cap in v1 (the per-field limits above already bound the realistic worst case) — outsized requests are rejected by WordPress/PHP/web-server configuration (`post_max_size`, etc.) before reaching this plugin. See [security.md](security.md#input-handling-wordpress-plugin).

**Success response — `201 Created`**

```json
{
  "post_id": 4821,
  "status": "draft",
  "title": "[INBOX] 半導体市況、AI需要で最高値更新",
  "edit_url": "https://your-site.example/wp-admin/post.php?post=4821&action=edit",
  "preview_url": "https://your-site.example/?p=4821&preview=true",
  "category": "素材候補",
  "created_at": "2026-07-27T09:15:03+09:00"
}
```

`edit_url` and `preview_url` are typed `string | null` — `get_edit_post_link()` / `get_preview_post_link()` can return falsy in some permission/environment combinations, and the client must handle that rather than assume a link is always present. There is no plain `link`/permalink field: a draft's `get_permalink()` is generally not a URL a human can usefully open, so it's intentionally omitted (see [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md)).

**Error responses**

| Status | Code | When | Who determines this |
|---|---|---|---|
| `400` | `missing_required_field` | `title` absent/empty after sanitization | plugin |
| `400` | `invalid_url` | `url` is present but fails validation | plugin |
| `400` | `invalid_shared_at` | `shared_at` present but doesn't match the required timestamp format | plugin |
| `400` | `https_required` | Request not made over HTTPS | plugin (checked before authentication) |
| `401` | *(WordPress's own error shape, e.g. `rest_not_logged_in`)* | Missing or invalid Application Password | **WordPress core**, not this plugin |
| `403` | `insufficient_capability` | Authenticated user lacks `edit_posts` | plugin |
| `409` | `category_unavailable` | The `素材候補` category configured at plugin activation no longer exists (e.g. deleted from wp-admin) | plugin |
| `422` | `duplicate_url` *(Phase 6+, flag-gated)* | An `[INBOX]` draft with the same `url` already exists — returned as a warning, not a hard block (see roadmap) | plugin |
| `500` | `insert_failed` | `wp_insert_post` returned a `WP_Error` | plugin |

Error body shape (standard WP REST error format):

```json
{
  "code": "invalid_url",
  "message": "The provided url is not a valid absolute URL.",
  "data": { "status": 400 }
}
```

### `GET /draft/{id}` *(Phase 2, read-back for the Android success screen — optional)*

Returns the created post's current status, for the app to confirm nothing was overwritten before the app's success screen renders `edit_url`. Same auth as above. Not required for v1 if the `201` response is trusted as-is; included here so the contract is reserved.

## Post creation semantics

| Draft field | Source |
|---|---|
| `post_title` | `[INBOX] {title}` (idempotent prefixing — see the `title` field notes above) |
| `post_status` | `draft` (always — never `publish`, regardless of any field the client sends; there is no code path capable of producing anything else, not just a validation rule — see [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md)) |
| `post_author` | The authenticated user (`get_current_user_id()`), always explicit — never left to `wp_insert_post`'s default |
| `post_content` | Structured body containing: original URL, **server-side** creation time, client-reported share time (if any), share source, memo (see template below) |
| `post_category` | The category configured at plugin **activation** time, re-verified to still exist at request time (`409 category_unavailable` if not) — **not** created or re-created during request handling |

Body template (server-rendered, not client-supplied HTML, to prevent injection — see security doc). Note the split between server time and client-reported time:

```
元URL: {url, line omitted entirely if url is empty}
保存日時: {server creation time, always}
共有日時: {client-reported shared_at, if provided — otherwise omitted}
共有元: {source}
メモ: {memo, or empty}

{shared_text, if present}
```

## XML-RPC fallback (`material_capture.createDraft`)

**Status: implemented (Phase 2c/2d, Android counterpart Phase 3c/3d).** An opt-in alternative to the REST endpoint above, for hosting environments that don't forward the `Authorization` header to PHP (confirmed on at least one real production host — see [docs/tech-decisions.md #11](tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport)). REST remains the default and recommended transport; this exists purely as a fallback, selected explicitly per site in the Android app's Settings screen. Verified end-to-end against production (dopodomani.biz) — see [docs/phase3-android-smoke-test-results.md](phase3-android-smoke-test-results.md).

**Endpoint:** the site's standard `xmlrpc.php`, over HTTPS only (same requirement as REST — see [security.md](security.md#transport-security)).

**Method:** `material_capture.createDraft`

**Params** (positional, XML-RPC array, matching the order below):

| Position | Type | Required | Notes |
|---|---|---|---|
| 0 | string | Yes | Username |
| 1 | string | Yes | Application Password (same credential as REST — WordPress core supports Application Passwords for XML-RPC natively, not just REST) |
| 2 | string | Yes | `title` |
| 3 | string or nil | No | `url` — optional since v1.1, see [docs/tech-decisions.md #12](tech-decisions.md#12-url-is-optional) |
| 4 | string or nil | No | `shared_text` |
| 5 | string or nil | No | `memo` |
| 6 | string or nil | No | `source` |
| 7 | string or nil | No | `shared_at` (same format requirement as REST) |

**Success response** — an XML-RPC `struct` with the same fields as the REST `201` body: `post_id`, `status`, `title`, `edit_url`, `preview_url`, `category`, `created_at` (see [Success response](#endpoints) above for field meanings — identical here).

**Faults** — XML-RPC has no native concept of an HTTP status or a `code` string, so this plugin uses `faultCode` numbers matching the REST status codes 1:1 for a consistent mental model (this is this plugin's own convention, not an XML-RPC standard), with `faultString` carrying the human-readable message:

| `faultCode` | Meaning | Who determines this |
|---|---|---|
| *(no fault; `wp_xmlrpc_server::login()` failure)* | Bad username/Application Password | **WordPress core's own error** (typically `faultCode` 403, "Incorrect username or password") — this plugin does not invent its own code for this case, mirroring the REST division of responsibility |
| 400 | `missing_required_field` (title only — `url` is optional) / `invalid_url` (only if `url` is present and malformed) / `invalid_shared_at` (message distinguishes which) | plugin |
| 400 | HTTPS required | plugin |
| 403 | `insufficient_capability` | plugin |
| 409 | `category_unavailable` | plugin |
| 500 | `insert_failed` | plugin |

Full class design, method signature, and test plan: [docs/phase2c-xmlrpc-design.md](phase2c-xmlrpc-design.md).

## Versioning & compatibility

- Namespace is `material-capture/v1` — a breaking change (removed field, changed semantics) ships as `v2` and both are served in parallel for a deprecation window.
- New optional fields may be added to `v1` without a version bump; clients must ignore unknown response fields and omit fields they don't support.

## Rate limiting

v1 has no built-in rate limiting (single-user, single-device usage pattern). If the plugin is exposed beyond a trusted personal site, front it with standard WordPress rate-limiting plugins or a reverse-proxy rule — noted as an operational recommendation in [security.md](security.md), not enforced in-plugin for v1.

## Future endpoints (reserved, not built in v1)

- `GET /destinations` — for a future multi-destination Android build to discover which sinks a server-side webhook variant supports.
- `POST /tags/suggest` — AI tag suggestion (Phase 6+), decoupled from `/draft` so it can be called before the user commits to Save.
