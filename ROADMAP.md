# Roadmap

Each phase gates the next — a phase is not started until the previous one is reviewed/accepted.

## Process

Every implementation phase (2 and 3) is split into a **design sub-stage** and a **build sub-stage**:

1. **Design sub-stage** — produce a reviewable design doc (no code). Deliverable is a single markdown file under `docs/` that can be read and approved on its own.
2. **Review gate** — the user reviews the design doc. Implementation does not start until the design is explicitly approved. If review changes the design, the **doc is updated first**, in its own commit, before any related code commit.
3. **Build sub-stage** — implement against the approved design. The phase's Definition of Done (below) must be fully met before moving to the next phase.

This mirrors the project's [CLAUDE.md](CLAUDE.md) rule: "docs before code," and design decisions get recorded in [docs/tech-decisions.md](docs/tech-decisions.md).

## Phase 1 — Design ✅ complete

**Definition of Done:**
- [x] Architecture ([docs/architecture.md](docs/architecture.md))
- [x] API specification ([docs/api-spec.md](docs/api-spec.md))
- [x] Technology decisions ([docs/tech-decisions.md](docs/tech-decisions.md))
- [x] Security policy ([docs/security.md](docs/security.md))
- [x] Repository layout ([README.md](README.md#repository-layout))
- [x] Design review sign-off → unblocked Phase 2

## Phase 2 — WordPress plugin

### Phase 2a — Detailed design ✅ complete

**Definition of Done:**
- [x] [docs/phase2-wordpress-plugin-design.md](docs/phase2-wordpress-plugin-design.md) written: concrete file/class layout, method signatures, validation rules, error mapping, activation/deactivation/uninstall behavior, test plan
- [x] Design reviewed and explicitly approved by the user — **implementation does not begin until this box is checked**

### Phase 2b — Implementation (current)

**Definition of Done:**
- [x] `composer.json` + PSR-4 skeleton (`Rest/`, `Application/`, `Domain/`, `Infrastructure/`) matching the approved design doc
- [x] `POST /wp-json/material-capture/v1/draft` implemented exactly per [docs/api-spec.md](docs/api-spec.md) (request/response shapes, status codes, error codes)
- [x] Application Password auth enforced (Basic Auth over HTTPS only) + `edit_posts` capability check in `permission_callback`
- [x] `素材候補` category auto-created on activation; clean `uninstall.php` removes only plugin options, never the category or posts
- [x] `post_status` hardcoded to `draft` server-side regardless of client input
- [x] All input fields sanitized/validated server-side per [docs/security.md](docs/security.md)
- [x] PHPUnit suite passes with no live WordPress instance required (39 tests, 96 assertions)
- [x] PHP lint / WordPress Coding Standards (PHPCS) run clean locally (with a documented, justified ruleset customization — see [docs/phase2-wordpress-plugin-design.md](docs/phase2-wordpress-plugin-design.md#phpcs-ruleset-customization))
- [ ] Manual smoke test against LocalWP or `wp-env` (Docker) covers the happy path and every documented error code — procedure and checklist ready in [docs/phase2-smoke-test-guide.md](docs/phase2-smoke-test-guide.md), results recorded in [docs/phase2-smoke-test-results.md](docs/phase2-smoke-test-results.md); **not yet run**
- [x] Any design deviation discovered during implementation is reflected back into `docs/phase2-wordpress-plugin-design.md` and `docs/api-spec.md` (if API-shaped) **before** the corresponding code is merged

### Phase 2c — XML-RPC fallback (design) ✅ complete

Added after main-PC smoke testing found that the project's production WordPress host does not forward the `Authorization` header to PHP even with the documented `.htaccess` rewrite rule — meaning Application-Password-over-REST cannot authenticate there at all. Confirmed by the project's own pre-existing `publish_wordpress_article.py`, which already works around identical hosting behavior using XML-RPC. See [docs/tech-decisions.md #11](docs/tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport) for the full rationale and why this amends, rather than reverses, ADR #2.

**Definition of Done:**
- [x] [docs/phase2c-xmlrpc-design.md](docs/phase2c-xmlrpc-design.md) written: XML-RPC method name/params/response/fault-code contract, class design, auth division of responsibility, test plan
- [x] `docs/api-spec.md`, `docs/security.md`, `docs/tech-decisions.md` updated accordingly
- [x] Design reviewed and explicitly approved by the user (2026-07-30, resolving the two open questions: register unconditionally, English `IXR_Error` messages) — implementation proceeds in Phase 2d

### Phase 2d — XML-RPC fallback (implementation, current)

**Definition of Done:**
- [x] `material_capture.createDraft` XML-RPC method registered via the `xmlrpc_methods` filter, delegating to the same `CreateDraftUseCase`/`DraftPayloadFactory` the REST controller uses — no duplicated business logic (`includes/XmlRpc/DraftXmlRpcHandler.php`)
- [x] Application Password authentication via `wp_xmlrpc_server::login()` (WordPress core supports Application Passwords for XML-RPC natively, per WP's own documentation)
- [x] `post_status`/`post_author` still always server-controlled, identical guarantee to the REST path (same `CreateDraftUseCase`/`get_current_user_id()`, no client-supplied override)
- [x] Fault codes match the table in `docs/api-spec.md`'s XML-RPC section
- [x] PHPUnit tests (Brain\Monkey + Mockery, same conventions as the REST controller's tests) pass with no live WordPress instance — `composer test` (46 tests) and `composer lint` (PHPCS) both green
- [x] Manual verification against the production host that XML-RPC succeeds where REST does not — confirmed 2026-07-31 (see docs/phase3-android-smoke-test-results.md), after fixing two real bugs found in the process: the `xmlrpc_init` registration hook (doesn't exist in WordPress core) and a wrong `createDraft` callback signature (`ArgumentCountError`)

## Phase 3 — Android Share Target app

### Phase 3a — Detailed design (current)

**Definition of Done:**
- [x] [docs/phase3-android-app-design.md](docs/phase3-android-app-design.md) written: screen transitions, Share Target flow, ViewModel/Repository(Destination) construction, Retrofit API, Hilt DI, error handling, Loading/Success/Error state transitions, Android test strategy
- [ ] Design reviewed and explicitly approved by the user — **implementation does not begin until this box is checked**

### Phase 3b — Implementation (current)

**Definition of Done:**
- [x] Project skeleton: `:core` (domain/data, plain Kotlin/JVM) + `:app` (presentation, Android) Gradle modules, Hilt wiring — matches the approved design doc plus the Gradle-module-layout addendum added at implementation start
- [x] Share Target intent filter (`ACTION_SEND`), extracts `title` / `url` / shared text — `IntentParser` + `AndroidManifest.xml`. **Written, not yet verified** (needs the Android SDK — see below)
- [x] Confirmation screen (Compose): editable title, URL, memo — `ConfirmDraftScreen`. **Written, not yet verified**
- [x] `Destination` interface + `WordPressDestination` implementation, calling `POST /draft` per [docs/api-spec.md](docs/api-spec.md) — **verified**: `gradle :core:test` passes in this environment (17 tests, incl. MockWebServer-based `WordPressDestinationTest`)
- [x] Application Password entry + `EncryptedSharedPreferences` storage (never logged, never hardcoded) — `SettingsScreen` + `EncryptedSettingsRepository`. **Written, not yet verified** (Robolectric test written but needs the Android SDK to run)
- [x] Unit tests for `domain` pass with no emulator required — **verified** in this environment (no Android SDK needed for `:core`)
- [x] `IntentParserTest` implemented (confirmed as a Phase 3a requirement) — **verified green on Android CI** (pinned `@Config(sdk = [34])`; Robolectric 4.13 doesn't yet support API 35, this app's compileSdk/targetSdk)
- [x] MockWebServer-based `data` layer tests — **verified**: `WordPressDestinationTest`, `MaterialCaptureErrorMapperTest` all pass
- [x] ktlint runs clean locally and on CI — **verified for both modules**
- [x] Any design deviation is reflected back into the Phase 3 design doc (and `docs/architecture.md` if the layering changes) before the corresponding code is merged — the `:core`/`:app` Gradle module split was added to the design doc before any `build.gradle.kts` was written
- [x] Android CI added ([.github/workflows/android-ci.yml](.github/workflows/android-ci.yml)) to close the verification gap the secondary PC can't: `:core:test`, `:core:ktlintCheck`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` all run on a real Android SDK on every push/PR — see [docs/development.md](docs/development.md#cis-role). **First run green** on [PR #1](https://github.com/dopodomani/wp-share-to-draft/pull/1) after fixing issues the SDK-less environment couldn't catch (see below).
- [ ] Main-PC smoke test (Android Studio + emulator/real device): real Share Target sheet, real Compose UI rendering, real end-to-end submission to a real WordPress instance. Procedure and checklist ready in [docs/phase3-android-smoke-test-guide.md](docs/phase3-android-smoke-test-guide.md), results recorded in [docs/phase3-android-smoke-test-results.md](docs/phase3-android-smoke-test-results.md); **not yet run**. This is what finally verifies the `ConfirmDraftScreen`/`SettingsScreen`/`IntentParser`-against-real-Chrome items marked "written, not yet verified" above — CI proves they *compile and pass unit tests*, not that they *render/behave correctly on a device*.

**Confirmed boundary in this environment, now closed by CI:** `:app:compileDebugKotlin` had failed here with `SDK location not found` — expected, since this environment has no Android SDK (see [docs/development.md](docs/development.md)). Android CI's first real run against the actual SDK caught several genuine bugs invisible without one: an illegal `--` inside an `AndroidManifest.xml` XML comment (broke the manifest merger), `:core`'s Retrofit/OkHttp/kotlinx.serialization dependencies declared `implementation` instead of `api` (hid those types from `:app`'s own code), two Compose files missing `import androidx.compose.runtime.getValue` (breaks `by collectAsState()`), `PasswordVisualTransformation` imported from the wrong package, and Robolectric 4.13 not yet supporting API 35 (fixed with `@Config(sdk = [34])` on both Robolectric tests). All fixed; `:core:test`, `:core:ktlintCheck`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` are now green together on GitHub Actions. **Still unverified even with CI green: real UI rendering, real Share Target sheet, real device/emulator behavior — that's exactly what the new main-PC smoke test item above covers.**

### Phase 3c — XML-RPC fallback (design) ✅ complete

Android-side counterpart to Phase 2c/2d: lets the user pick REST or XML-RPC per site in Settings, since some hosting (confirmed against the project's own production host) can't authenticate REST's Basic Auth at all.

**Definition of Done:**
- [x] [docs/phase3c-android-xmlrpc-design.md](docs/phase3c-android-xmlrpc-design.md) written: `ConnectionMethod` setting, `WordPressPublisher`/`WordPressPublisherFactory` selection design, hand-rolled XML-RPC request/response codec (no new library dependency), Settings screen changes, test plan
- [x] `docs/phase3-android-app-design.md` cross-referenced/updated where the `Destination`/Settings design is affected
- [x] Design reviewed and explicitly approved by the user (with amendments: `WordPressPublisher` naming, `XML_RPC` default, `Logger` port) — implementation proceeds in Phase 3d

### Phase 3d — XML-RPC fallback (implementation, current)

**Definition of Done:**
- [x] `AppSettings` gains a `connectionMethod` field (default `XML_RPC`, `REST` alternative); Settings screen exposes the choice, XML-RPC listed first
- [x] `WordPressPublisher` interface + `RestPublisher`/`XmlRpcPublisher` implementations; `WordPressPublisherFactory` selects between them from current settings; `WordPressDestination` (unchanged name, unchanged `Destination` binding) delegates to the factory — `ConfirmDraftViewModel`/`SubmitCaptureUseCase` unchanged
- [x] `Logger` port added to `:core` (Android-free) with an `AndroidLogger` implementation in `:app`; `WordPressDestination` logs "Publishing via XML_RPC"/"Publishing via REST" on every send
- [x] MockWebServer-based tests for the XML-RPC path, mirroring the existing REST test's coverage
- [x] No automatic REST↔XML-RPC fallback or auto-detection anywhere
- [x] ktlint clean; `:core:test`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` all pass locally — CI still to confirm on the PR
- [x] Manual verification against the production host that switching to XML-RPC succeeds where REST does not — confirmed 2026-07-31; a `NetworkOnMainThreadException` and a Japanese IME composition bug in the Confirm screen were found and fixed in the process (see docs/phase3-android-smoke-test-results.md)

## Phase 4 — Integration testing

**Status: on hold (2026-08-03).** Deliberately not pursued as work in this repository right now — the happy path (real Android device → real Chrome share → real WordPress) has already been exercised repeatedly and successfully during Phase 3d/2d verification against the project's production host, so the most valuable part of Phase 4b is informally covered already. Formal Phase 4a/4b work (design doc, systematic error-path testing, explicit CI-pipeline-boundary verification) can resume whenever it's next prioritized; nothing below is stale, just paused. Local-WordPress dev tooling (`wordpress-plugin/scripts/setup-local-sqlite-env.sh`) was still built in the meantime, since it's independently useful and not itself Phase 4 scope.

### Phase 4a — Integration test design

**Definition of Done:**
- [ ] `docs/phase4-integration-test-design.md` written: test environment choice (e.g. `wp-env`/Docker), scenario list (REST route registration, auth/authz against real capability checks, real `wp_insert_post` post creation, Android device → real Chrome share → real WP), and explicit pass/fail criteria per scenario
- [ ] Design reviewed and explicitly approved by the user — integration tests are not written or run until this box is checked

### Phase 4b — Execution (blocked until 4a is approved)

**Definition of Done:**
- [ ] Real Android device (USB debugging) → real Chrome share → real or LocalWP/Docker WordPress instance, happy path confirmed end-to-end
- [ ] Error-path testing covers: no network, invalid credentials, WordPress unreachable, oversized payload, duplicate submission
- [ ] Confirm the existing GitHub Actions pipeline still fires correctly off the created draft (no change expected, but the boundary is explicitly verified, not assumed)
- [ ] Any gap found here that implies a design or API change is written back into the relevant `docs/` file before being fixed in code

## Phase 5 — OSS launch

**Definition of Done:**
- [ ] `LICENSE` (MIT), `CONTRIBUTING.md`, `CHANGELOG.md` finalized for public contributors
- [x] Issue templates, PR template added under `.github/`
- [x] GitHub Actions WordPress CI: locked Composer install, PHPUnit on PHP 8.1/8.2/8.3, Composer validation, and PHPCS — [`.github/workflows/wordpress-ci.yml`](.github/workflows/wordpress-ci.yml)
- [x] Dependency maintenance: Android Version Catalog, weekly Dependabot updates for Gradle/Composer/Actions, and full-SHA pinning for every third-party Action
- [x] Documentation CI: Markdown lint, link checking, and Mermaid syntax checking — [`.github/workflows/docs-ci.yml`](.github/workflows/docs-ci.yml)
- [x] Release packaging produces a verified `material-capture/`-rooted plugin ZIP, Android APK, and SHA-256 checksums, publishing them on `v*` tags — [`.github/workflows/release.yml`](.github/workflows/release.yml)
- [ ] README polished so an external contributor can onboard from it alone
- [ ] Repository visibility confirmed public

## Phase 6+ — Platform expansion (post-launch)

Ideas to grow this from "an app that posts to WordPress" into a general AI-era news-capture front end. Each is additive against the `Destination` / capture-source boundaries established in Phase 1 — none require rearchitecting the core.

- [ ] **Voice memo capture** — record a short voice note at share time, transcribe (on-device or via API), fold into the `memo` field
- [ ] **AI tag suggestions** — suggest categories/tags (e.g. 半導体, AIインフラ, 車載) before Save, via a new `/tags/suggest` endpoint (reserved in [api-spec.md](docs/api-spec.md#future-endpoints-reserved-not-built-in-v1))
- [ ] **Duplicate detection** — warn if an `[INBOX]` draft with the same URL already exists (`422 duplicate_url`, non-blocking)
- [ ] **Custom post type** — let the user choose a dedicated "素材" post type instead of a plain post, per-request
- [ ] **Pluggable destinations** — `GithubDestination`, `NotionDestination`, `SlackDestination`, `WebhookDestination` alongside `WordPressDestination`
- [ ] **PWA capture source** — Web Share Target API front end reusing the same REST contract and (where feasible) the same Kotlin domain logic via Kotlin Multiplatform
