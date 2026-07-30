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

- The plugin **refuses non-HTTPS requests** at the controller level (checks `is_ssl()`), even if the server is misconfigured to accept plain HTTP on the REST route — defense in depth, not reliance on server config alone. This returns the plugin's own `400 https_required` (see [api-spec.md](api-spec.md#endpoints)) — distinct from WordPress's own authentication error family, since it's a transport precondition, not an identity check.
- **Reverse proxy / CDN caveat:** `is_ssl()` reflects what WordPress believes about the current request, which is only correct behind a reverse proxy or CDN if the server is configured to translate `X-Forwarded-Proto` (or equivalent) into WordPress's expected `$_SERVER['HTTPS']`. Self-hosters running behind a proxy must configure this at the web-server/proxy level — the plugin cannot detect or correct a misconfigured proxy, and this is called out explicitly rather than assumed to work.
- Android's `network_security_config.xml` disallows cleartext traffic (`cleartextTrafficPermitted="false"`) so the app cannot accidentally be pointed at an `http://` endpoint.

## Credential storage (Android)

- The Application Password is stored via **`EncryptedSharedPreferences`** (Jetpack Security), backed by the Android Keystore — never in plain `SharedPreferences`, never logged.
- Never included in crash reports or analytics. Logging statements are code-reviewed for accidental credential leakage before each release (manual checklist item, not automated in v1).
- No credential is ever put in a URL query string (also required by the general assistant privacy rules this project is built under).

## Input handling (WordPress plugin)

- All incoming fields pass through explicit sanitizers before use: `sanitize_text_field` for title/memo, `esc_url_raw` for the URL, `sanitize_key` for `source`, each with its own server-side length cap (see [api-spec.md](api-spec.md#endpoints) for the exact per-field limits, measured in `mb_strlen` characters — the primary content here is Japanese text, so byte-length caps would be the wrong unit).
- `post_content` is built entirely from a server-side template (see [api-spec.md](api-spec.md#post-creation-semantics)) — the client never supplies raw HTML that gets stored or rendered unescaped. This forecloses stored-XSS via the shared-text/memo fields.
- `post_status` is hardcoded to `draft`, and `post_author` is always the authenticated user — the client cannot force `publish` or attribute a post to another user no matter what it sends. There is no parameter in the domain/service layer capable of expressing either, not just a validation rule that happens to reject it.
- **No application-level total request-body-size cap in v1.** The per-field length caps above already bound the realistic worst case; outsized requests are left to WordPress/PHP/web-server limits (`post_max_size`, `upload_max_filesize`, etc.) rather than the plugin re-implementing a size check whose failure mode (how do you return clean JSON for a request the web server already truncated or rejected?) is genuinely ambiguous and better left to server configuration. Revisit only if real-world abuse patterns justify it.

## Authorization

- The REST route requires `edit_posts` capability on the authenticated user (checked in the `permission_callback`, per WordPress REST API convention — never inside the handler as an afterthought).
- No endpoint accepts an arbitrary WordPress user ID or "act as" parameter — the post author is always the authenticated user.

## XML-RPC fallback transport (Phase 2c/2d, designed not yet built)

An opt-in alternative to REST for hosts that strip the `Authorization` header before it reaches PHP (see [docs/tech-decisions.md #11](tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport)). This section documents its security posture explicitly, since XML-RPC as a *general WordPress feature* has a materially different risk profile than REST — see [ADR #2](tech-decisions.md#2-rest-api-not-xml-rpc):

- **Same credential, same transport security.** Application Passwords are used exactly as they are for REST — no new credential type, no new storage on the Android side. HTTPS is still required; a plain-HTTP XML-RPC request must be rejected server-side, mirroring the REST path's `https_required` check.
- **This plugin does not enable XML-RPC on a site that doesn't already have it enabled**, and does not change the site's existing XML-RPC exposure. If `xmlrpc.php` is already reachable (as it must be, for this fallback to work at all), the well-known XML-RPC-wide risks (`system.multicall` amplification for brute-force, pingback-based SSRF/DDoS via `pingback.ping`) are pre-existing site-level exposure this plugin's one additional authenticated method does not materially increase — but it also does nothing to reduce them. Operators relying on this fallback are relying on a transport this project would not otherwise recommend turning on; **only enable it if REST is genuinely unusable on your host**, and consider a security plugin that restricts XML-RPC to specific methods if available.
- **The new method itself carries no elevated risk beyond REST's own:** it requires the same Application Password, the same `edit_posts` capability check, hardcodes `post_status` to `draft` and `post_author` to the authenticated user identically to the REST path — see [docs/phase2c-xmlrpc-design.md](phase2c-xmlrpc-design.md) for the class-level detail once written.
- **Bad-credential handling is WordPress core's, not this plugin's**, exactly mirroring the REST division of responsibility (see [Authentication method comparison](#authentication-method-comparison) above) — `wp_xmlrpc_server::login()` fails closed before this plugin's own method body ever runs.

## Threat model summary

| Threat | Mitigation |
|---|---|
| Stolen/leaked Application Password | Revoke instantly from wp-admin per-device; password only grants this user's capabilities, not admin/site-wide secrets |
| Man-in-the-middle on the API call | HTTPS enforced both client- and server-side; no cleartext fallback |
| Malicious payload creating public content | `post_status` hardcoded server-side to `draft` |
| Stored XSS via shared text/memo | Server-side templated body, all fields sanitized/escaped before storage and before render in wp-admin |
| Credential theft from a compromised Android device | Keystore-backed encrypted storage; out of scope beyond standard Android app sandboxing (not defending against a rooted/compromised device) |
| Abuse of the endpoint from an unrelated caller | Requires valid Application Password + capability check; recommend WAF/rate-limit plugin if the site is broadly reachable (see [api-spec.md](api-spec.md#rate-limiting)) |
| Plugin activated then abandoned, leaving stale data/hooks | `uninstall.php` removes only the plugin's own options — it never deletes the `素材候補` category or any posts, even if the category is empty. A same-named category may predate the plugin or be reused by the site owner for other purposes, so deleting by name (or even by a "we created it" flag) is judged not worth the risk for v1; see [phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md) for the full rationale. Data ownership stays with the site. |
| Enabling the XML-RPC fallback re-exposes XML-RPC-wide risks (brute-force amplification, pingback SSRF) | This plugin doesn't newly enable `xmlrpc.php` — it only adds one authenticated method to a transport the site owner already had reachable. Documented as an explicit trade-off; REST stays the recommended default, XML-RPC is opt-in per site (see the section above) |

## Non-goals for v1

- Multi-tenant / multi-site authorization model.
- Built-in rate limiting or WAF functionality (recommended as an operational add-on, not built into the plugin).
- Protecting against a fully compromised/rooted Android device or a malicious WordPress admin — both are outside this project's trust boundary.

## Reporting

Once the repository is public (Phase 5), security issues should be reported privately (GitHub private vulnerability reporting) rather than as public issues. This section will be expanded into a `SECURITY.md` at that point.
