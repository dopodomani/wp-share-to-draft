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

| Field | Type | Required | Notes |
|---|---|---|---|
| `title` | string | Yes | Original page title. Plugin prefixes it with `[INBOX] ` server-side — client does **not** send the prefix. |
| `url` | string (URL) | Yes | Must be a valid absolute URL; validated with `esc_url_raw` server-side. |
| `shared_text` | string | No | Raw text captured from the Android `ACTION_SEND` extra, if the source app included any (e.g. a highlighted excerpt). |
| `memo` | string | No | User-entered note from the confirmation screen. |
| `source` | string | No | Free-form origin tag, e.g. `chrome-share`, `pwa`, `webhook`. Defaults to `unknown`. Used for the "共有元" field and future per-source analytics. |
| `shared_at` | string (ISO 8601) | No | Client-side share timestamp. If omitted, the server uses receipt time. Stored as "保存日時" alongside WordPress's own `post_date`. |

**Success response — `201 Created`**

```json
{
  "id": 4821,
  "status": "draft",
  "title": "[INBOX] 半導体市況、AI需要で最高値更新",
  "link": "https://your-site.example/?p=4821",
  "edit_link": "https://your-site.example/wp-admin/post.php?post=4821&action=edit",
  "category": "素材候補",
  "created_at": "2026-07-27T09:15:03+09:00"
}
```

**Error responses**

| Status | Code | When |
|---|---|---|
| `400` | `missing_required_field` | `title` or `url` absent/empty |
| `400` | `invalid_url` | `url` fails validation |
| `401` | `rest_forbidden` / `invalid_credentials` | Missing or bad Application Password |
| `403` | `insufficient_capability` | Authenticated user lacks `edit_posts` |
| `413` | `payload_too_large` | Body exceeds configured max (default 256 KB — accommodates `shared_text`, still bounded) |
| `422` | `duplicate_url` *(Phase 6+, flag-gated)* | An `[INBOX]` draft with the same `url` already exists — returned as a warning, not a hard block (see roadmap) |
| `500` | `insert_failed` | `wp_insert_post` returned a `WP_Error` |

Error body shape (standard WP REST error format):

```json
{
  "code": "invalid_url",
  "message": "The provided url is not a valid absolute URL.",
  "data": { "status": 400 }
}
```

### `GET /draft/{id}` *(Phase 2, read-back for the Android success screen — optional)*

Returns the created post's current status, for the app to confirm nothing was overwritten before the app's success screen renders `edit_link`. Same auth as above. Not required for v1 if the `201` response is trusted as-is; included here so the contract is reserved.

## Post creation semantics

| Draft field | Source |
|---|---|
| `post_title` | `[INBOX] {title}` |
| `post_status` | `draft` (always — never `publish`, regardless of any field the client sends) |
| `post_content` | Structured body containing: original URL, share timestamp, share source, memo (see template below) |
| `post_category` | Term `素材候補` (created on plugin activation if missing) |

Body template (server-rendered, not client-supplied HTML, to prevent injection — see security doc):

```
元URL: {url}
保存日時: {shared_at or receipt time}
共有元: {source}
メモ: {memo, or empty}

{shared_text, if present}
```

## Versioning & compatibility

- Namespace is `material-capture/v1` — a breaking change (removed field, changed semantics) ships as `v2` and both are served in parallel for a deprecation window.
- New optional fields may be added to `v1` without a version bump; clients must ignore unknown response fields and omit fields they don't support.

## Rate limiting

v1 has no built-in rate limiting (single-user, single-device usage pattern). If the plugin is exposed beyond a trusted personal site, front it with standard WordPress rate-limiting plugins or a reverse-proxy rule — noted as an operational recommendation in [security.md](security.md), not enforced in-plugin for v1.

## Future endpoints (reserved, not built in v1)

- `GET /destinations` — for a future multi-destination Android build to discover which sinks a server-side webhook variant supports.
- `POST /tags/suggest` — AI tag suggestion (Phase 6+), decoupled from `/draft` so it can be called before the user commits to Save.
