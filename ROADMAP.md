# Roadmap

Each phase gates the next — a phase is not started until the previous one is reviewed/accepted.

## Phase 1 — Design (current)

- [x] Architecture ([docs/architecture.md](docs/architecture.md))
- [x] API specification ([docs/api-spec.md](docs/api-spec.md))
- [x] Technology decisions ([docs/tech-decisions.md](docs/tech-decisions.md))
- [x] Security policy ([docs/security.md](docs/security.md))
- [x] Repository layout ([README.md](README.md#repository-layout))
- [ ] Design review sign-off → unblocks Phase 2

## Phase 2 — WordPress plugin

- [ ] `composer.json` + PSR-4 skeleton (`Rest/`, `Domain/`, `Support/`)
- [ ] `POST /wp-json/material-capture/v1/draft` per [api-spec.md](docs/api-spec.md)
- [ ] Application Password auth guard + `edit_posts` capability check
- [ ] `素材候補` category auto-created on activation
- [ ] Clean `uninstall.php`
- [ ] PHPUnit suite for `Domain/` (no live WP required)
- [ ] PHP lint / static analysis wired locally (CI comes in Phase 5, or earlier if trivial)

## Phase 3 — Android Share Target app

- [ ] Project skeleton: `presentation` / `domain` / `data` modules, Hilt wiring
- [ ] Share Target intent filter (`ACTION_SEND`), extract `title` / `url` / shared text
- [ ] Confirmation screen (Compose): editable title, URL, memo
- [ ] `Destination` interface + `WordPressDestination` implementation
- [ ] Application Password entry + `EncryptedSharedPreferences` storage
- [ ] Unit tests for `domain` (no emulator required)

## Phase 4 — Integration testing

- [ ] Real Android device, USB debugging, real Chrome share → real (or LocalWP/Docker) WordPress instance
- [ ] Error-path testing: no network, invalid credentials, WP unreachable, oversized payload
- [ ] Confirm existing GitHub Actions pipeline still fires correctly off the created draft (no change expected, but verify the boundary)

## Phase 5 — OSS launch

- [ ] `LICENSE` (MIT), `CONTRIBUTING.md`, `CHANGELOG.md` filled in
- [ ] Issue templates, PR template
- [ ] GitHub Actions CI: PHP lint, ktlint, Markdown lint
- [ ] README polish for external contributors
- [ ] Public repository visibility

## Phase 6+ — Platform expansion (post-launch)

Ideas to grow this from "an app that posts to WordPress" into a general AI-era news-capture front end. Each is additive against the `Destination` / capture-source boundaries established in Phase 1 — none require rearchitecting the core.

- [ ] **Voice memo capture** — record a short voice note at share time, transcribe (on-device or via API), fold into the `memo` field
- [ ] **AI tag suggestions** — suggest categories/tags (e.g. 半導体, AIインフラ, 車載) before Save, via a new `/tags/suggest` endpoint (reserved in [api-spec.md](docs/api-spec.md#future-endpoints-reserved-not-built-in-v1))
- [ ] **Duplicate detection** — warn if an `[INBOX]` draft with the same URL already exists (`422 duplicate_url`, non-blocking)
- [ ] **Custom post type** — let the user choose a dedicated "素材" post type instead of a plain post, per-request
- [ ] **Pluggable destinations** — `GithubDestination`, `NotionDestination`, `SlackDestination`, `WebhookDestination` alongside `WordPressDestination`
- [ ] **PWA capture source** — Web Share Target API front end reusing the same REST contract and (where feasible) the same Kotlin domain logic via Kotlin Multiplatform
