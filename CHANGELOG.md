# Changelog

All notable changes to this project are documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added
- Phase 1 design docs: architecture, API spec, tech decisions, security policy, roadmap.
- Per-phase Definition of Done and a design-review-gate process in ROADMAP.md.
- Phase 2 detailed design for the WordPress plugin (docs/phase2-wordpress-plugin-design.md), pending review.
- Phase 2 design decisions locked: PHP 8.1 minimum, Brain\Monkey + Mockery test roles, 素材候補 as standard category taxonomy.
- Phase 4 split into a design sub-stage (4a) gating execution (4b), matching Phases 2/3.

- `material-capture` WordPress plugin implemented (Domain/Application/Infrastructure/Rest), with a 39-test PHPUnit suite (Brain\Monkey + Mockery) and a clean, justified-customization PHPCS pass.
- Phase 2b smoke test guide and dated results template (docs/phase2-smoke-test-guide.md, docs/phase2-smoke-test-results.md) for verifying the plugin against a real WordPress instance (LocalWP or `wp-env`).
- Phase 3a detailed design for the Android Share Target app (docs/phase3-android-app-design.md), approved.
- ADR #10: kotlinx.serialization as the Android JSON library (docs/tech-decisions.md).
- Development environment docs: main/secondary PC roles, git branching, CI role, Android-SDK-dependency matrix (docs/development.md); Claude Code/Codex role division (docs/ai-development.md).
- Android app implemented in two Gradle modules: `:core` (domain + data — CaptureItem, Destination, SettingsRepository, CaptureError, SubmitCaptureUseCase, Retrofit API/DTOs, WordPressDestination, MaterialCaptureErrorMapper, AuthInterceptor) and `:app` (presentation — IntentParser, ConfirmDraftViewModel/SettingsViewModel, Compose screens, ShareReceiverActivity, Hilt DI, EncryptedSettingsRepository). `:core` verified in this environment (17 tests, ktlint clean); `:app` requires the Android SDK to build (main PC/CI) — see docs/development.md.
- Android CI (.github/workflows/android-ci.yml): on push to `main`/PRs touching `android/**`, runs `:core:test`, `:core:ktlintCheck`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` on Ubuntu with a real Android SDK — closes the verification gap the secondary PC has for `:app`. No secrets required (network tests use MockWebServer only). First run green on [PR #1](https://github.com/dopodomani/wp-share-to-draft/pull/1).
- Phase 3b Android smoke test guide and dated results template (docs/phase3-android-smoke-test-guide.md, docs/phase3-android-smoke-test-results.md) for verifying the app on the main PC (Android Studio, emulator/real device, real WordPress instance) — the layer CI can't cover (real UI rendering, real Share Target sheet, real device behavior).
- docs/testing.md: testing strategy overview tying together WordPress and Android unit tests, CI, and manual smoke tests into one four-layer picture, with a pointer table to where each side's test plan actually lives.
- ADR #11: XML-RPC as an opt-in fallback transport (docs/tech-decisions.md), amending ADR #2 after production smoke testing found a real host whose `Authorization`-header stripping makes REST's Basic Auth unusable even with the documented `.htaccess` fix.
- Phase 2c/3c detailed design for the XML-RPC fallback (docs/phase2c-xmlrpc-design.md — WordPress `material_capture.createDraft` method; docs/phase3c-android-xmlrpc-design.md — Android `ConnectionMethod`/`CompositeWordPressDestination`), pending review. `docs/api-spec.md` and `docs/security.md` updated with the XML-RPC contract and threat-model additions; ROADMAP.md gained Phase 2c/2d and 3c/3d.
- Phase 3c design revised (revision 2) after user review: `WordPressPublisher`/`RestPublisher`/`XmlRpcPublisher`/`WordPressPublisherFactory` naming, default `ConnectionMethod` switched to `XML_RPC`, and a new `Logger` port added so `:core` stays Android-free while still logging which transport is used.
- Phase 3d implemented: `:core` gained `ConnectionMethod`/`Logger`/`WordPressPublisher`/`RestPublisher`/`XmlRpcPublisher`/`MaterialCaptureXmlRpcApi`/`WordPressPublisherFactory` (36 tests, ktlint clean); `WordPressDestination` is now a thin dispatcher over the factory. `:app` gained `AndroidLogger`, a connection-method radio picker in the Settings screen (XML-RPC listed first/default), and `connectionMethod` persistence in `EncryptedSettingsRepository`. No automatic REST↔XML-RPC fallback. `:app:testDebugUnitTest`/`:app:lintDebug`/`:app:assembleDebug` pass locally; manual verification against the production host is blocked on Phase 2d (WordPress-side XML-RPC method not yet implemented).

### Changed
- Phase 2 design revised after design review: split `Support/` into `Application/` (orchestration + ports) and `Infrastructure/` (WordPress adapters); made `Domain/DraftPayload` fully dependency-free; Mockery targets are now interfaces only; category lifecycle is create-once-at-activation and never-delete; `InputSanitizer` testing split into unit (delegation + own logic) vs. integration (real sanitization); API response/error shapes and field limits made concrete (see docs/phase2-wordpress-plugin-design.md and docs/api-spec.md for details).
- Phase 3a design revised after review: `CredentialRepository`/`Credentials` renamed to `SettingsRepository`/`AppSettings`; intent parsing extracted into its own `IntentParser` class with a dedicated test; `CaptureItem`'s role as the central domain model made explicit; `CaptureError.NetworkUnavailable` split into a `Network` sub-hierarchy (`Timeout`/`DnsFailure`/`SslFailure`/`Unreachable`).

### Fixed
- Bugs only a real Android SDK build could catch, found and fixed via Android CI's first run: an illegal `--` inside an `AndroidManifest.xml` XML comment; `:core`'s Retrofit/OkHttp/kotlinx.serialization dependencies were `implementation` instead of `api`, hiding those types from `:app`; two Compose files missing `import androidx.compose.runtime.getValue`; `PasswordVisualTransformation` imported from the wrong package; Robolectric 4.13 not yet supporting API 35 (pinned both Robolectric tests to `@Config(sdk = [34])`).
