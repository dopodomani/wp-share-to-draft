# Technology Decisions

ADR-style rationale for the major choices. Each entry: decision, alternatives considered, why.

## 1. WordPress plugin, not a child theme

**Decision:** Standalone plugin under `wordpress-plugin/`.

**Alternatives considered:** child theme `functions.php` hook; must-use plugin.

**Why:** A child theme couples this feature to whichever theme is active — switching themes silently breaks capture. A plugin is independently activatable/deactivatable, uninstalls cleanly, and is the conventional distribution unit for "adds a REST endpoint" functionality, matching how the rest of the WordPress ecosystem (and future users installing this OSS project) expect to install it.

## 2. REST API, not XML-RPC

**Decision:** WordPress REST API (`register_rest_route`).

**Alternatives considered:** XML-RPC.

**Why:** XML-RPC is legacy, frequently disabled/blocked by security plugins and hosts by default, and has a worse security reputation (historically a common brute-force/amplification target). The REST API is the actively maintained, JSON-native, capability-integrated interface WordPress has standardized on since 4.7 — better client ergonomics on Android (plain JSON via Retrofit) and better long-term support.

**Amended (see [ADR #11](#11-xml-rpc-as-an-opt-in-fallback-transport)):** REST remains the default and recommended transport. XML-RPC was added back as an explicit, user-selected fallback after a real production host was found where REST's Basic Auth cannot work at all — this ADR's reasoning against XML-RPC as the *only or default* transport still stands.

## 3. Application Passwords for auth

**Decision:** WordPress core Application Passwords over HTTPS Basic Auth.

**Why:** See [security.md](security.md#authentication-method-comparison) for the full comparison against JWT/OAuth1/custom keys. Summary: zero extra plugin dependency, per-device revocation, matches this project's single-user/single-site usage pattern.

## 4. Kotlin + native Share Target, not a wrapped web view

**Decision:** Native Android app in Kotlin, registering an `ACTION_SEND` intent filter.

**Alternatives considered:** A PWA-only approach from day one; a WebView-wrapped hybrid app.

**Why:** Android's Share Target (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) is a first-class OS integration point — it appears in every app's native share sheet, including Chrome, with zero extra setup from the user. A PWA "share target" (the Web Share Target API) requires the PWA to be installed and has more inconsistent support across Android versions/browsers as of 2026. Starting native gets the core use case (share from Chrome) working reliably now; the **domain layer is still built Android-agnostic** (see [architecture.md](architecture.md)) so a PWA share target can be added later as a second presentation layer without touching business logic.

Kotlin over Java: current Android-recommended language, better null-safety (relevant for a data-in-from-Intent-extras app where fields are frequently absent), first-class Compose support for the confirmation screen.

## 5. Clean Architecture + explicit `Destination` interface

**Decision:** presentation / domain / data layering on Android; domain defines a `Destination` port, data provides the first (`WordPressDestination`) adapter.

**Why:** The user's own stated roadmap includes GitHub, Notion, Obsidian, and Slack as future send targets, and a PWA/webhook as future capture sources. Without an explicit port/adapter boundary, "add a destination" tends to mean forking conditional logic through the UI layer. With the interface in place, adding `NotionDestination` is additive — a new file implementing `Destination`, wired via DI — with no change to `ConfirmDraftViewModel`, `SubmitCaptureUseCase`, or the confirmation screen. This directly serves the "AI時代のニュース収集基盤" (news-collection platform, not single-purpose app) framing the user wants this project to grow into.

## 6. Dependency Injection: Hilt (Android), manual constructor injection (PHP)

**Decision:** Hilt for the Android app; the WordPress plugin uses plain constructor injection wired in the plugin bootstrap file (no DI container).

**Why:** Hilt is the Android-recommended DI framework, integrates with `ViewModel`/`Activity` lifecycles with minimal boilerplate, and is what makes swapping `Destination` implementations (goal above) trivial and testable via test modules. On the PHP side, WordPress plugins conventionally avoid heavyweight DI containers (extra dependency, slower activation, unfamiliar to most WP plugin reviewers); the class graph here is small enough (`Controller` → `Service` → `Repository`/`Sanitizer`) that manual wiring in one bootstrap function is simpler to read and audit than introducing a container.

## 7. Networking: Retrofit + OkHttp (Android)

**Decision:** Retrofit for the `MaterialCaptureApi` client, OkHttp underneath for interceptors (auth header injection, logging in debug builds only).

**Why:** Standard, well-tested combination for typed REST calls in Kotlin; interceptor pattern keeps Basic Auth header injection in one place rather than repeated per-call; OkHttp's `CertificatePinner` is available later if the project wants to add cert pinning for a specific known WordPress host (not required for v1, an OSS tool talking to arbitrary user-configured sites).

## 8. Testing composition

**Decision:** Domain layer (both sides) is pure logic with no framework dependency, enabling plain JUnit/Kotlin tests on Android and plain PHPUnit tests (with WordPress function stubs, e.g. via `WP_Mock` or Brain\Monkey) on the plugin side — no emulator, no live WordPress instance required for the majority of the test suite.

**Why:** Directly required by the project's stated quality bar ("ユニットテスト可能な構造"). Isolating `wp_insert_post`, `wp_set_object_terms`, etc. behind `WpPostRepository` is what makes the plugin's core business rule ("build `[INBOX]` draft with category X") testable without bootstrapping WordPress in CI. Instrumented/integration tests (real device → real WP, or `wp-env`) are reserved for Phase 4, layered on top of this fast unit suite, not a replacement for it.

## 9. Composer for the plugin, Gradle (Kotlin DSL) for Android

**Decision:** `composer.json` at `wordpress-plugin/` root for PHP dependency management (autoloading, PHPUnit, static analysis tools); `build.gradle.kts` for Android.

**Why:** Both are the de facto standard tooling for their ecosystems; no alternative seriously considered. Composer's PSR-4 autoloading is what makes the `Rest/Application/Domain/Infrastructure` namespace layout in the directory structure work cleanly (see [docs/phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md#layering) for why it's four layers, not the three originally sketched here).

## 10. JSON library: kotlinx.serialization

**Decision:** `kotlinx.serialization` (via `retrofit2-kotlinx-serialization-converter`) for the Android app's DTO (de)serialization, over Moshi or Gson.

**Alternatives considered:** Moshi (Square, code-gen or reflection-based, widely used with Retrofit); Gson (older, fully reflection-based, no longer actively recommended for new projects by its own maintainers).

**Why:** `kotlinx.serialization` is Kotlin-native (part of the Kotlin project itself, not a third-party addition), requires no reflection at runtime and no separate annotation-processing build step (unlike Moshi's codegen or Gson's reflection), and integrates with Retrofit via an official converter. Given this project's DTOs are small and stable (mirroring [docs/api-spec.md](api-spec.md) exactly, per [docs/phase3-android-app-design.md](phase3-android-app-design.md#5-retrofit-api)), none of Moshi's extra codegen tooling or Gson's broader reflection-based flexibility is needed — the simpler, build-time-checked option was preferred. Confirmed in Phase 3a review (2026-07-28).

## 11. XML-RPC as an opt-in fallback transport

**Decision:** Add a `material_capture.createDraft` XML-RPC method to the WordPress plugin, and let the Android app pick REST or XML-RPC per site in Settings (`ConnectionMethod`), with REST remaining the default.

**Context:** During Phase 3b main-PC smoke testing against the project's own production WordPress site (dopodomani.biz), REST requests consistently returned this plugin's own `insufficient_capability` error with **401** even when a verified-correct Application Password was sent — the tell that `is_user_logged_in()` was false, i.e. WordPress never authenticated the request at all. The documented `Authorization`-header-forwarding `.htaccess` fix (see [docs/security.md](security.md#transport-security)) was applied and did not resolve it — this host does not forward the `Authorization` header to PHP at any layer we can control from `.htaccess`. This is corroborated by this project's own pre-existing, unrelated `publish_wordpress_article.py` script, which already had to use XML-RPC for the same reason on the same host.

**Alternatives considered:**
- **Do nothing, treat it as a hosting-specific limitation out of scope.** Rejected — this is the user's own real deployment target, not a hypothetical edge case, and the constraint (Basic Auth header stripped by the host) is common enough on shared/managed WordPress hosting to be worth designing around rather than dismissing.
- **Drop REST entirely, make XML-RPC the only transport.** Rejected — would contradict [ADR #2](#2-rest-api-not-xml-rpc)'s reasoning wholesale (many hosts actively disable XML-RPC by default for security reasons; REST is fine and preferable on hosts without this specific header-forwarding bug). REST support is kept in full; XML-RPC is additive, not a replacement.
- **Have the Android app call WordPress's built-in `wp.newPost` XML-RPC method directly, bypassing the plugin.** Rejected — would move business rules (`[INBOX]` prefix, `素材候補` category assignment, forcing draft status, `post_author`) out of the server-owned `CreateDraftService` and into the Android client, duplicating logic the REST path already centralizes server-side, and reopening exactly the "client could request something other than a draft" risk [docs/security.md](security.md#input-handling-wordpress-plugin) was designed to foreclose. Confirmed with the user: keep a custom, server-side XML-RPC method sharing the same `CreateDraftUseCase`/`DraftPayloadFactory` as REST.

**Why this shape:** WordPress core's Application Password feature is documented to work anywhere a username/password would otherwise be used, including XML-RPC (not REST-exclusive) — so the *same* Application Password the user already has works unchanged for both transports; nothing new to provision. Registering our own method via the `xmlrpc_methods` filter, rather than reusing `wp.newPost`, keeps `Domain`/`Application` (see [docs/phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md#layering)) as the single owner of "what a draft looks like" — `Rest/DraftController` and the new `XmlRpc/DraftXmlRpcHandler` become two thin adapters over the same business logic, exactly the pattern already established for keeping WordPress-specific code out of `Domain`/`Application`. On the Android side, `Destination` stays a single interface; a `WordPressPublisher` strategy (`RestPublisher`/`XmlRpcPublisher`, chosen by `WordPressPublisherFactory`) sits behind the single `WordPressDestination`, so nothing above the `data` layer (`SubmitCaptureUseCase`, `ConfirmDraftViewModel`) needs to know a second transport exists at all.

**Default connection method is `XML_RPC`, not `REST`** (confirmed in the Phase 3c/3d review round, 2026-07-30): a fresh install defaults to the transport already confirmed to work end-to-end against this project's own reference deployment, rather than the one confirmed broken there. This doesn't change anything about REST's standing as a fully-supported, first-class option — it's purely which radio button is pre-selected in Settings.
