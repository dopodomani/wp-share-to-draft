# Security Policy & Threat Model

## Scope

This covers the Android app ↔ WordPress plugin channel and the plugin's handling of incoming data. It does not cover WordPress core/hosting hardening in general (assume the site is already reasonably maintained) or anything downstream of the draft post.

## Authentication method comparison

The client (Android app, and later PWA/webhook) needs to authenticate to a single WordPress site as a single user. Options considered:

| Method | Pros | Cons | Verdict |
|---|---|---|---|
| **Application Passwords** (WP core ≥5.6) | Built into core, no extra plugin; per-device, independently revocable from wp-admin; scoped to one user's capabilities; standard Basic Auth, trivial to implement on both ends | Basic Auth means the credential is sent (hashed only by TLS) on every request — **mandates HTTPS**; no built-in scope/permission restriction narrower than the user account | **Chosen.** Matches the single-user, single-site, personal-use profile of this project; zero extra attack surface from a third-party auth plugin. |
| JWT (via a JWT Auth plugin) | Short-lived tokens, no credential resent every call | Requires installing and trusting a third-party auth plugin, a secret key to manage, refresh-token flow to implement on Android — meaningfully more code and more moving parts for a personal single-user tool | Rejected for v1 — revisit only if multi-user support is ever needed. |
| OAuth1 (WooCommerce-style) | Strong delegated-auth model | Designed for third-party app marketplaces; large implementation overhead for signature generation on Android; no benefit at this project's scale | Rejected. |
| Custom API key/secret in a plugin option | Simple | Reinvents credential storage/rotation that Application Passwords already provide for free, with worse revocation UX (no per-device list) | Rejected. |

**Conclusion:** Application Passwords, enforced over HTTPS, with the plugin additionally checking the authenticated user has `edit_posts` capability before doing anything.

## Transport security

- The plugin **refuses non-HTTPS requests** at the controller level (checks `is_ssl()`), even if the server is misconfigured to accept plain HTTP on the REST route — defense in depth, not reliance on server config alone.
- Android's `network_security_config.xml` disallows cleartext traffic (`cleartextTrafficPermitted="false"`) so the app cannot accidentally be pointed at an `http://` endpoint.

## Credential storage (Android)

- The Application Password is stored via **`EncryptedSharedPreferences`** (Jetpack Security), backed by the Android Keystore — never in plain `SharedPreferences`, never logged.
- Never included in crash reports or analytics. Logging statements are code-reviewed for accidental credential leakage before each release (manual checklist item, not automated in v1).
- No credential is ever put in a URL query string (also required by the general assistant privacy rules this project is built under).

## Input handling (WordPress plugin)

- All incoming fields pass through explicit sanitizers before use: `sanitize_text_field` for title/memo/source, `esc_url_raw` for the URL, with server-side length caps.
- `post_content` is built entirely from a server-side template (see [api-spec.md](api-spec.md#post-creation-semantics)) — the client never supplies raw HTML that gets stored or rendered unescaped. This forecloses stored-XSS via the shared-text/memo fields.
- `post_status` is hardcoded to `draft` in the service layer; the client cannot force `publish` no matter what it sends — even a compromised or buggy client can only ever create drafts, never public content.
- Request body size is capped (default 256 KB) to bound worst-case `shared_text` payloads and reduce trivial DoS surface from a single caller.

## Authorization

- The REST route requires `edit_posts` capability on the authenticated user (checked in the `permission_callback`, per WordPress REST API convention — never inside the handler as an afterthought).
- No endpoint accepts an arbitrary WordPress user ID or "act as" parameter — the post author is always the authenticated user.

## Threat model summary

| Threat | Mitigation |
|---|---|
| Stolen/leaked Application Password | Revoke instantly from wp-admin per-device; password only grants this user's capabilities, not admin/site-wide secrets |
| Man-in-the-middle on the API call | HTTPS enforced both client- and server-side; no cleartext fallback |
| Malicious payload creating public content | `post_status` hardcoded server-side to `draft` |
| Stored XSS via shared text/memo | Server-side templated body, all fields sanitized/escaped before storage and before render in wp-admin |
| Credential theft from a compromised Android device | Keystore-backed encrypted storage; out of scope beyond standard Android app sandboxing (not defending against a rooted/compromised device) |
| Abuse of the endpoint from an unrelated caller | Requires valid Application Password + capability check; recommend WAF/rate-limit plugin if the site is broadly reachable (see [api-spec.md](api-spec.md#rate-limiting)) |
| Plugin activated then abandoned, leaving stale data/hooks | Clean `uninstall.php` removes the plugin's added term/options; deactivation does not delete existing draft posts (data ownership stays with the site) |

## Non-goals for v1

- Multi-tenant / multi-site authorization model.
- Built-in rate limiting or WAF functionality (recommended as an operational add-on, not built into the plugin).
- Protecting against a fully compromised/rooted Android device or a malicious WordPress admin — both are outside this project's trust boundary.

## Reporting

Once the repository is public (Phase 5), security issues should be reported privately (GitHub private vulnerability reporting) rather than as public issues. This section will be expanded into a `SECURITY.md` at that point.
