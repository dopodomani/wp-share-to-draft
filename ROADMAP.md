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

## Phase 3 — Android Share Target app

### Phase 3a — Detailed design (current)

**Definition of Done:**
- [x] [docs/phase3-android-app-design.md](docs/phase3-android-app-design.md) written: screen transitions, Share Target flow, ViewModel/Repository(Destination) construction, Retrofit API, Hilt DI, error handling, Loading/Success/Error state transitions, Android test strategy
- [ ] Design reviewed and explicitly approved by the user — **implementation does not begin until this box is checked**

### Phase 3b — Implementation (blocked until 3a is approved)

**Definition of Done:**
- [ ] Project skeleton: `presentation` / `domain` / `data` modules, Hilt wiring, matching the approved design doc
- [ ] Share Target intent filter (`ACTION_SEND`), extracts `title` / `url` / shared text
- [ ] Confirmation screen (Compose): editable title, URL, memo
- [ ] `Destination` interface + `WordPressDestination` implementation, calling `POST /draft` per [docs/api-spec.md](docs/api-spec.md)
- [ ] Application Password entry + `EncryptedSharedPreferences` storage (never logged, never hardcoded)
- [ ] Unit tests for `domain` pass with no emulator required
- [ ] ktlint runs clean locally
- [ ] Any design deviation is reflected back into the Phase 3 design doc (and `docs/architecture.md` if the layering changes) before the corresponding code is merged

## Phase 4 — Integration testing

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
- [ ] Issue templates, PR template added under `.github/`
- [ ] GitHub Actions CI: PHP lint, ktlint, Markdown lint, all green on `main`
- [ ] Release packaging step produces a `material-capture/`-rooted zip from the `wordpress-plugin/` source (see [docs/phase2-wordpress-plugin-design.md](docs/phase2-wordpress-plugin-design.md#release-packaging-source-layout-vs-installed-plugin-folder))
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
