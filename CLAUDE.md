# CLAUDE.md

Guidance for AI-assisted development (Claude Code and others) working in this repository. This is a condensed, durable version of the project's OSS guidelines — the full rationale for each design choice lives in [docs/tech-decisions.md](docs/tech-decisions.md).

## Priorities

Long-term maintainability, readability, and extensibility come before speed of implementation. When in doubt, prefer the simpler design (YAGNI) that still respects the extension points below — don't build for hypothetical futures beyond what's listed in [ROADMAP.md](ROADMAP.md).

## Design

- Apply SOLID; keep responsibilities separated (see the layering in [docs/architecture.md](docs/architecture.md)).
- Keep dependencies explicit and pointing inward (presentation → domain ← data), never the reverse.
- Avoid premature abstraction — three similar lines beats a speculative interface no current phase needs.

## Documentation

- Write or update docs **before** writing code for a design change. A reader should be able to understand the system from README.md alone; docs/ holds the depth.
- Use Mermaid diagrams liberally for architecture/flow.
- When a design changes, update README.md and the relevant docs/ file in the **same commit/PR** as the code change — never let them drift.
- Record every non-trivial design decision (and its rejected alternatives) in [docs/tech-decisions.md](docs/tech-decisions.md), ADR-style.
- If something is ambiguous, don't guess — present it as an explicit design choice (in docs or by asking) rather than silently picking one.

## API

- `material-capture/v1` and any future namespace must stay backward compatible; breaking changes require a new version (`v2`), served alongside the old one during a deprecation window.
- Design new endpoints so they're additive, not a rework of existing ones — see [docs/api-spec.md](docs/api-spec.md#future-endpoints-reserved-not-built-in-v1) for reserved examples.
- All responses are JSON, following the shapes documented in [docs/api-spec.md](docs/api-spec.md).

## Android

- Keep UI (`presentation/`) and business logic (`domain/`) separate; `domain/` must stay pure Kotlin with no Android framework imports.
- Minimize Android framework dependencies inside `domain/` and `data/` business logic — confine them to `presentation/` and thin adapters.
- Every use case and destination implementation must be unit-testable without an emulator.

## WordPress

- Ship as a standalone plugin under `wordpress-plugin/` — never as `functions.php` code in a theme.
- Follow WordPress Coding Standards (PHPCS with `WordPress` ruleset — configured in `wordpress-plugin/phpcs.xml.dist`, run via `composer lint`).
- Center the plugin around its REST API (`Rest/` controllers); keep WordPress core calls (`wp_insert_post`, etc.) isolated behind repository classes (e.g. `WpPostRepository`) so `Domain/` stays testable without a live WordPress instance.

## Security

- Never commit secrets. Application Passwords are entered by the user at runtime and stored in `EncryptedSharedPreferences` on Android — never hardcoded, never logged, never in this repository.
- Validate and sanitize every input field server-side (see [docs/security.md](docs/security.md)), regardless of client-side validation.
- Design assumes HTTPS-only; the plugin must reject plaintext HTTP requests server-side, not just rely on server config.
- Every REST route requires an explicit capability/permission check in its `permission_callback` — never only inside the handler.

## Git

- Keep commits small and topical.
- Use [Conventional Commits](https://www.conventionalcommits.org/): `docs:`, `feat:`, `fix:`, `refactor:`, `test:`, `chore:`.

## Testing

- Design for testability from the start: both the Android `domain/` layer and the WordPress plugin's `Domain/` layer must be unit-testable in isolation (no emulator, no live WordPress).
- Integration/instrumented tests (real device, real or local WordPress) are a later, additive layer (Phase 4) — not a substitute for the unit suite.

## Extensibility

- Never hardcode WordPress as the only destination or Android as the only capture source. New send targets (GitHub, Notion, Slack, Webhook) are added as new `Destination` implementations; new capture sources (PWA, webhook) reuse the same `material-capture` REST contract. See [docs/architecture.md](docs/architecture.md#extension-points-why-this-shape-supports-the-roadmap).

## Delivery approach

Build MVP → improve → extend, per [ROADMAP.md](ROADMAP.md)'s phases. Don't skip ahead to Phase 6+ features while an earlier phase is incomplete. Optimize for other contributors being able to join easily — clear docs, small PRs, testable code.
