# Phase 2 Design — WordPress Plugin (`material-capture`)

**Status: awaiting review.** This document is the reviewable design artifact for Phase 2a. No plugin code exists yet — per [ROADMAP.md](../ROADMAP.md#process), implementation (Phase 2b) starts only after this doc is explicitly approved. It refines [docs/architecture.md](architecture.md#wordpress-plugin--layering) and [docs/api-spec.md](api-spec.md) into concrete files, classes, and method signatures, without writing the implementation itself.

## Plugin identity

| | |
|---|---|
| Plugin slug | `material-capture` |
| Plugin folder | `wordpress-plugin/` (repo root — becomes `wp-content/plugins/material-capture/` when installed) |
| Bootstrap file | `wordpress-plugin/material-capture.php` |
| Text domain | `material-capture` |
| Minimum WP | 6.0 (Application Passwords stable since 5.6; 6.0 floor gives headroom on REST API maturity) |
| Minimum PHP | 8.1 (matches the dev-environment guidance already given: PHP 8.3 recommended, 8.1 as the floor for broader host compatibility) |
| Namespace root | `MaterialCapture\` (PSR-4, autoloaded via Composer) |

## File layout

```
wordpress-plugin/
├── material-capture.php          # Plugin header, bootstrap, activation/deactivation hooks
├── uninstall.php                 # Runs on delete; removes category + options
├── composer.json                 # PSR-4 autoload: "MaterialCapture\\": "includes/"
├── includes/
│   ├── Plugin.php                # Wires everything together (composition root)
│   ├── Rest/
│   │   ├── DraftController.php   # WP_REST_Controller: route registration, arg schema
│   │   └── RestResponseFactory.php # Builds success/error JSON shapes per api-spec.md
│   ├── Domain/
│   │   ├── CreateDraftService.php # Business rule: build + persist an [INBOX] draft
│   │   ├── DraftPayload.php       # Value object: validated input (title, url, memo, ...)
│   │   └── DraftResult.php        # Value object: id, status, title, link, edit_link, category, created_at
│   └── Support/
│       ├── InputSanitizer.php     # Per-field sanitization (title, url, memo, source, shared_text)
│       ├── PostBodyTemplate.php   # Renders post_content from a DraftPayload (server-side template)
│       ├── WpPostRepositoryInterface.php
│       └── WpPostRepository.php   # Only class calling wp_insert_post / wp_set_object_terms / term_exists
└── tests/
    ├── bootstrap.php              # Loads Composer autoload + Brain\Monkey/WP_Mock setup
    ├── Domain/
    │   ├── CreateDraftServiceTest.php
    │   └── DraftPayloadTest.php
    └── Support/
        ├── InputSanitizerTest.php
        └── PostBodyTemplateTest.php
```

`Rest/` depends on `Domain/`; `Domain/` depends on `Support/` interfaces only (not concrete WP calls); `Support/WpPostRepository` is the sole place touching WordPress core post functions. This mirrors the layering already agreed in [docs/architecture.md](architecture.md#wordpress-plugin--layering) and is what makes `Domain/` testable under plain PHPUnit with no live WordPress.

## Class responsibilities & signatures

### `Plugin.php` (composition root)

```php
final class Plugin {
    public static function activate(): void;     // registers 素材候補 category if missing
    public static function deactivate(): void;    // no-op: data ownership stays with the site
    public function registerRoutes(): void;       // hooked to rest_api_init
}
```

No DI container (per [docs/tech-decisions.md](tech-decisions.md#6-dependency-injection-hilt-android-manual-constructor-injection-php)) — `Plugin::registerRoutes()` manually constructs `DraftController(new CreateDraftService(new WpPostRepository(), new InputSanitizer(), new PostBodyTemplate()))`.

### `Rest/DraftController.php`

```php
final class DraftController extends WP_REST_Controller {
    protected $namespace = 'material-capture/v1';
    protected $rest_base = 'draft';

    public function __construct(private CreateDraftService $service) {}

    public function register_routes(): void;
    // registers POST /draft with args schema for: title, url, shared_text, memo, source, shared_at

    public function permission_callback(WP_REST_Request $request): bool|WP_Error;
    // true only if: is_ssl() AND current_user_can('edit_posts')
    // returns WP_Error('rest_forbidden', ..., ['status' => 401|403]) otherwise per api-spec.md

    public function create_draft(WP_REST_Request $request): WP_REST_Response|WP_Error;
    // delegates to CreateDraftService::create(), maps DraftResult -> 201 JSON,
    // catches domain exceptions -> maps to the error codes/status in api-spec.md
}
```

Args schema (registered via WP's own REST arg validation, so malformed types are rejected before reaching `Domain/`):

| arg | type | required | validate_callback | sanitize_callback |
|---|---|---|---|---|
| `title` | string | yes | non-empty after trim | `sanitize_text_field` |
| `url` | string | yes | `wp_http_validate_url` | `esc_url_raw` |
| `shared_text` | string | no | max length check | `sanitize_textarea_field` |
| `memo` | string | no | max length check | `sanitize_textarea_field` |
| `source` | string | no | max length check | `sanitize_key`-style allowlist, default `unknown` |
| `shared_at` | string | no | valid ISO 8601 (`DateTimeImmutable::createFromFormat`) | passthrough (already validated) |

This two-stage validation (REST arg schema, then `DraftPayload` construction in `Domain/`) is intentional: the arg schema is WordPress-idiomatic and gives free, uniform 400 errors; `DraftPayload` re-validates because it must also be constructible directly in unit tests without going through the REST layer.

### `Domain/DraftPayload.php` (value object)

```php
final class DraftPayload {
    private function __construct(
        public readonly string $title,
        public readonly string $url,
        public readonly ?string $sharedText,
        public readonly ?string $memo,
        public readonly string $source,
        public readonly DateTimeImmutable $sharedAt,
    ) {}

    /** @throws InvalidPayloadException */
    public static function fromArray(array $data, InputSanitizer $sanitizer): self;
}
```

`InvalidPayloadException` carries a machine-readable `$code` (`missing_required_field`, `invalid_url`) matching [api-spec.md](api-spec.md#error-responses) exactly, so `DraftController` can map it to the documented HTTP status/error body without re-deriving the mapping.

### `Domain/CreateDraftService.php`

```php
final class CreateDraftService {
    public function __construct(
        private WpPostRepositoryInterface $posts,
        private InputSanitizer $sanitizer,
        private PostBodyTemplate $bodyTemplate,
    ) {}

    /** @throws InvalidPayloadException|DraftCreationFailedException */
    public function create(DraftPayload $payload): DraftResult;
    // 1. title = '[INBOX] ' . payload.title
    // 2. body = bodyTemplate->render(payload)
    // 3. category term id = posts->ensureCategory('素材候補')
    // 4. post id = posts->insertDraft(title, body, [category term id])
    // 5. returns DraftResult(id, 'draft', title, permalink, edit link, '素材候補', now)
}
```

`post_status` is never a parameter here — `insertDraft()` on the repository interface has no status argument; it is implicitly always `draft`. This is the concrete mechanism behind the security requirement "client cannot force publish" ([docs/security.md](security.md#input-handling-wordpress-plugin)): there is no code path in `Domain/` capable of producing anything but a draft, so it cannot be broken by a future bug in argument handling.

### `Support/WpPostRepositoryInterface.php` / `WpPostRepository.php`

```php
interface WpPostRepositoryInterface {
    public function ensureCategory(string $name): int;       // term_exists() / wp_insert_term()
    public function insertDraft(string $title, string $body, array $categoryIds): int; // wp_insert_post(), always status=draft
    public function permalink(int $postId): string;          // get_permalink()
    public function editLink(int $postId): string;            // get_edit_post_link()
}
```

Only `WpPostRepository` (the concrete class) calls WordPress core functions. `CreateDraftServiceTest` uses a hand-written test double implementing the interface — no WP bootstrap needed for the domain test suite.

### `Support/InputSanitizer.php`

One method per field (`sanitizeTitle`, `sanitizeUrl`, `sanitizeMemo`, `sanitizeSharedText`, `sanitizeSource`), each a thin, individually testable wrapper so `DraftPayloadTest` can assert sanitization behavior without needing WordPress's own sanitize functions loaded (test doubles substitute in unit tests; real WP functions used in integration/Phase 4 testing).

### `Support/PostBodyTemplate.php`

```php
final class PostBodyTemplate {
    public function render(DraftPayload $payload): string;
    // Produces exactly the template documented in api-spec.md:
    // 元URL / 保存日時 / 共有元 / メモ / (shared_text, if present)
    // All values already sanitized before reaching here — this class does formatting only, no escaping decisions.
}
```

## Activation / deactivation / uninstall

- **Activation** (`register_activation_hook`): `Plugin::activate()` ensures the `素材候補` category exists (idempotent — checked via `term_exists` first).
- **Deactivation** (`register_deactivation_hook`): no-op. Deactivating must not touch existing posts/categories/data — matches [docs/security.md](security.md#threat-model-summary) ("deactivation does not delete existing draft posts").
- **Uninstall** (`uninstall.php`, run only on explicit delete from wp-admin): removes the `素材候補` category **only if it has no posts assigned**, and removes any plugin options added in later phases. Existing `[INBOX]` draft posts are never deleted by the plugin itself — the user owns that data.

## Error mapping (controller → api-spec.md)

| Domain condition | HTTP status | `code` |
|---|---|---|
| `title` or `url` missing/empty | 400 | `missing_required_field` |
| `url` fails `wp_http_validate_url` | 400 | `invalid_url` |
| Not authenticated / bad Application Password | 401 | `invalid_credentials` |
| Authenticated but lacks `edit_posts` | 403 | `insufficient_capability` |
| Request over plain HTTP | 401 | `rest_forbidden` (treated as unauthenticated — HTTPS is a precondition of trust, not a separate error family) |
| Body exceeds 256 KB | 413 | `payload_too_large` |
| `wp_insert_post` returns `WP_Error` | 500 | `insert_failed` |

This table is the single source of truth the controller's catch blocks implement against — any future change to error semantics updates this table and [api-spec.md](api-spec.md#error-responses) together, per the docs-before-code rule.

## Test plan

### Tooling and roles (confirmed 2026-07-27)

- **PHPUnit** is the test runner for the whole `Domain`/`Support`/`Rest` unit suite.
- **Brain\Monkey** stubs and sets expectations on WordPress *functions and hooks* specifically — `wp_insert_post`, `current_user_can`, `sanitize_text_field`, `add_action`, `term_exists`, `wp_set_object_terms`, etc. It owns anything that is a bare WordPress function call or hook registration.
- **Mockery** mocks *object* dependencies within this plugin's own code — `WpPostRepositoryInterface`, `InputSanitizer`, `PostBodyTemplate` — wherever a class under test collaborates with another class rather than calling a WordPress function directly. `CreateDraftServiceTest` is the primary consumer: it mocks `WpPostRepositoryInterface` via Mockery rather than stubbing `wp_insert_post` via Brain\Monkey, since `CreateDraftService` never calls WordPress functions directly (only `WpPostRepository` does — see [File layout](#file-layout)).

### Testing philosophy

Tests target **this project's own behavior**, not WordPress's internals:

- Inputs → outputs (given a `DraftPayload`, does `CreateDraftService` build the right title/body/category and return the right `DraftResult`?).
- Post-generation rules (`[INBOX] ` prefix, server-side `draft` status, body template shape).
- Error conversion (a repository failure or invalid payload maps to the exact `code`/status in [Error mapping](#error-mapping-controller--api-specmd)).

Tests avoid over-asserting on *how many times* or *in what internal order* WordPress functions are called (e.g. do not assert `wp_insert_post` was called "exactly once with these exact 47 argument combinations" beyond what the behavior actually requires) — that couples the suite to WordPress's implementation details rather than this plugin's contract, and makes the suite brittle across WP core versions without adding real regression protection.

### Unit tests (Phase 2b Definition of Done — no live WordPress)

- `DraftPayloadTest`: valid input constructs successfully; missing `title`/`url` throws `InvalidPayloadException` with the right `$code`; invalid URL throws with `invalid_url`; optional fields default correctly (`source` → `unknown`, `sharedAt` → construction time).
- `CreateDraftServiceTest` (Mockery mock of `WpPostRepositoryInterface`): title is prefixed with `[INBOX] `; category is requested via `ensureCategory('素材候補')`; `insertDraft` is always called with a draft-implying signature (no way to pass `publish`); returned `DraftResult` fields match the mock's return values; repository failure (mock throws) surfaces as `DraftCreationFailedException`.
- `InputSanitizerTest` (Brain\Monkey stubs for `sanitize_text_field`/`esc_url_raw`/etc.): each sanitize method strips/escapes as expected on a table of adversarial inputs (script tags, oversized strings, malformed URLs) — asserting the *result*, not the stub call count.
- `PostBodyTemplateTest`: rendered body matches the exact template from api-spec.md for a payload with and without `shared_text`/`memo`. Pure formatting logic, no WordPress stubbing needed.
- `DraftControllerTest` (Brain\Monkey for `current_user_can`/`is_ssl`, Mockery for `CreateDraftService`): `permission_callback` returns the correct allow/deny per [Error mapping](#error-mapping-controller--api-specmd) for each of (HTTPS+capable, HTTPS+incapable, plain HTTP) — verifying the *decision*, not internal WP call sequencing.

### Integration test scope (designed separately, gates Phase 4)

Unit tests above deliberately stop short of anything requiring a real WordPress runtime. Per the user's direction, integration tests — REST route registration, authentication/authorization against real WordPress capability checks, and actual post creation via a real `wp_insert_post` — are **designed as their own reviewable document**, not folded into this one. [ROADMAP.md](../ROADMAP.md) now gates Phase 4 the same way as Phases 2/3: a **Phase 4a design sub-stage** produces `docs/phase4-integration-test-design.md` (test environment choice — e.g. `wp-env`/Docker —, scenarios, and pass/fail criteria) for review before any integration test is written or run.

## Non-goals for Phase 2

- No admin settings UI screen (Application Password is generated/managed entirely through WordPress's own user profile screen).
- No rate limiting (per [docs/api-spec.md](api-spec.md#rate-limiting), operational recommendation only).
- No duplicate-URL detection, no custom post type, no tag suggestion — all explicitly Phase 6+ ([ROADMAP.md](../ROADMAP.md#phase-6--platform-expansion-post-launch)).
- No GitHub Actions CI for this plugin yet (Phase 5); Phase 2b DoD only requires PHPCS/PHPUnit runnable locally.

## Design decisions confirmed in review (2026-07-27)

1. **PHP minimum version: 8.1.** Declared plugin minimum is PHP 8.1 for host compatibility; development/testing is done on PHP 8.3 as recommended in the dev-environment setup.
2. **PHPUnit WP-stubbing: Brain\Monkey.** Used with Mockery for stubbing `wp_insert_post`, `wp_set_object_terms`, `term_exists`, etc. in `Domain`/`Support` unit tests.
3. **`素材候補` is a standard `category` taxonomy term**, not a custom taxonomy. `WpPostRepository::ensureCategory()` is implemented against the built-in `category` taxonomy (`term_exists`/`wp_insert_term`/`wp_set_object_terms` with `'category'`).

These are now locked for Phase 2b implementation. Any further change to them must update this section (and `composer.json`'s `require.php` once written) before the corresponding code changes.
