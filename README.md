# wp-share-to-draft

> Capture anything you read on your phone into a WordPress draft, tagged `[INBOX]`, in two taps — the first step of an AI-assisted news-to-article pipeline.

**Status:** 🚧 Phase 2 (WordPress plugin implemented, pending a real-WordPress smoke test) / Phase 3a (Android design approved, implementation starting) — see [ROADMAP.md](ROADMAP.md).

[日本語版はこちら](#日本語)

---

## The problem

You're reading news on your phone (Nikkei, etc.) and spot something worth turning into an article later. Today that means:

1. Copy the URL
2. Open WordPress
3. Create a draft manually
4. Paste, title it, remember to mark it as "to process"

That friction means most good material never gets captured. `wp-share-to-draft` collapses this to: **tap Share → confirm → done.**

## What this project is

An Android app that registers as a **Share Target**. When you share a page from Chrome (or any app), it:

1. Extracts `title`, `url`, and any `shared text`
2. Shows a short confirmation screen (title / URL / memo, editable)
3. POSTs it to a **WordPress plugin's REST API endpoint**
4. The plugin creates a `draft` post titled `[INBOX] <original title>`, with the source URL, timestamp, share origin, and memo in the body, categorized under "素材候補" (Material Candidate)

From there, your existing GitHub Actions pipeline (unchanged, out of scope for this project) picks it up: draft → material note generation → Codex → finished article.

```
Android Chrome share  →  [this project]  →  WordPress draft [INBOX]  →  GitHub Actions  →  material note  →  Codex  →  article
                         └──────────────┘
                          scope ends here
```

## What this project is *not* (yet)

- Not Android-only by design — the Android app is the first of several planned **capture sources** (see below)
- Not a WordPress child theme — it's a standalone, deactivatable **plugin**
- Not a replacement for anything downstream of the WordPress draft

## Architecture at a glance

Two independently deployable components, talking over a single versioned REST contract:

- **`android/`** — Kotlin app, Share Target intent filter, Clean Architecture (presentation / domain / data), sends drafts to a *pluggable* destination (WordPress today; GitHub/Notion/Slack tomorrow, via a `Destination` interface)
- **`wordpress-plugin/`** — Standalone WP plugin, exposes `POST /wp-json/material-capture/v1/draft`, authenticated via WordPress Application Passwords

Full diagrams and layer breakdown: [docs/architecture.md](docs/architecture.md)
API contract: [docs/api-spec.md](docs/api-spec.md)
Why these choices: [docs/tech-decisions.md](docs/tech-decisions.md)
Threat model & auth rationale: [docs/security.md](docs/security.md)
WordPress plugin detailed design (Phase 2): [docs/phase2-wordpress-plugin-design.md](docs/phase2-wordpress-plugin-design.md)
WordPress plugin smoke test guide (Phase 2b): [docs/phase2-smoke-test-guide.md](docs/phase2-smoke-test-guide.md)
Android app detailed design (Phase 3, approved): [docs/phase3-android-app-design.md](docs/phase3-android-app-design.md)
Android app smoke test guide (Phase 3b): [docs/phase3-android-smoke-test-guide.md](docs/phase3-android-smoke-test-guide.md)
Testing strategy overview (both sides, all layers): [docs/testing.md](docs/testing.md)
Development environment & multi-machine workflow: [docs/development.md](docs/development.md)
Claude Code / Codex roles: [docs/ai-development.md](docs/ai-development.md)

## Repository layout

```
wp-share-to-draft/
├── README.md
├── LICENSE                    # MIT
├── CHANGELOG.md
├── CONTRIBUTING.md
├── ROADMAP.md
├── docs/
│   ├── architecture.md                    # diagrams, layering, extension points
│   ├── api-spec.md                        # REST API contract (material-capture/v1)
│   ├── tech-decisions.md                  # ADR-style rationale for every major choice
│   ├── security.md                        # auth method comparison, threat model, hardening
│   ├── phase2-wordpress-plugin-design.md  # WordPress plugin detailed design
│   ├── phase2-smoke-test-guide.md         # manual smoke test procedure + checklist
│   ├── phase2-smoke-test-results.md       # dated smoke test run records
│   ├── phase3-android-app-design.md       # Android app detailed design
│   ├── phase3-android-smoke-test-guide.md # manual smoke test procedure + checklist (main PC)
│   ├── phase3-android-smoke-test-results.md # dated smoke test run records
│   ├── testing.md                         # testing strategy overview (both sides, all layers)
│   ├── development.md                     # main/secondary PC roles, branching, CI
│   └── ai-development.md                  # Claude Code / Codex role division
├── android/                   # Kotlin app (Phase 3)
│   ├── app/
│   │   └── src/main/kotlin/.../
│   │       ├── presentation/  # ShareReceiverActivity, IntentParser, Compose screens, ViewModels
│   │       ├── domain/        # CaptureItem, Destination/SettingsRepository interfaces, use cases
│   │       └── data/          # WordPressDestination, DI modules, DTOs, EncryptedSettingsRepository
│   └── build.gradle.kts
├── wordpress-plugin/          # PHP plugin (Phase 2, implemented)
│   ├── material-capture.php   # plugin bootstrap
│   ├── includes/
│   │   ├── Rest/              # REST controller
│   │   ├── Application/       # use case, ports, DraftPayloadFactory
│   │   ├── Domain/             # dependency-free value objects, exceptions
│   │   └── Infrastructure/     # WordPress adapters (post repo, sanitizer, body template)
│   ├── tests/
│   └── composer.json
├── examples/                  # sample requests, Postman/HTTPie collections
└── .github/                   # issue/PR templates, CI workflows (added Phase 2+)
```

## Development environment

This project is developed across a **main PC** (full Android Studio/SDK/emulator, for anything Android-SDK-dependent) and a **secondary PC** (JDK + Gradle only, for `domain`/`application`-layer work on both the Android and WordPress sides, which needs no Android SDK). Two AI coding assistants — Claude Code (architecture/multi-file/docs coherence) and Codex (scoped review/fixes/CI triage) — share the work under an explicit division of labor. [Android CI](.github/workflows/android-ci.yml) fills the gap the secondary PC can't: it builds `:app` on a real Android SDK on every push/PR.

Full details: [docs/development.md](docs/development.md) (machine roles, git branching, PC-switching procedure, CI's role, what does/doesn't need the Android SDK) and [docs/ai-development.md](docs/ai-development.md) (Claude Code vs. Codex responsibilities and coordination rules).

## Roadmap (short version)

| Phase | Deliverable |
|---|---|
| 1 | Design (this document set) |
| 2 | WordPress plugin |
| 3 | Android Share Target app |
| 4 | Integration testing (real device → real WP) |
| 5 | OSS launch (v1.0, polished docs, templates, CI) |
| 6+ | Voice memo, AI tag suggestions, duplicate detection, custom post type, pluggable destinations (GitHub/Notion/Slack/Webhook) |

Details: [ROADMAP.md](ROADMAP.md)

## Contributing

Not yet open for contributions — the API and plugin surface are still being designed. `CONTRIBUTING.md` will be filled in at Phase 5 (OSS launch). Issues/discussion are welcome once the repository is public.

## License

MIT — see [LICENSE](LICENSE).

---

## 日本語

Android ChromeなどでWebページを「共有」した2タップで、WordPressに `[INBOX]` 付きの下書きを自動作成するプロジェクトです。

**現在のフェーズ:** WordPressプラグイン実装済み（実WordPress環境でのスモークテスト待ち）、Android設計承認済み（Phase 3b実装着手）。

**やること:** Android共有 → 確認画面 → WordPress下書き作成、まで。
**やらないこと:** それ以降（GitHub Actions以降の素材ノート生成・Codex・記事作成）は既存の運用のまま変更しません。

設計ドキュメント:
- アーキテクチャ: [docs/architecture.md](docs/architecture.md)
- API仕様: [docs/api-spec.md](docs/api-spec.md)
- 技術選定理由: [docs/tech-decisions.md](docs/tech-decisions.md)
- セキュリティ方針: [docs/security.md](docs/security.md)
- WordPressプラグイン設計: [docs/phase2-wordpress-plugin-design.md](docs/phase2-wordpress-plugin-design.md)
- Androidアプリ設計: [docs/phase3-android-app-design.md](docs/phase3-android-app-design.md)
- 開発環境（メイン/サブPC、Claude Code/Codex運用）: [docs/development.md](docs/development.md) / [docs/ai-development.md](docs/ai-development.md)
- ロードマップ: [ROADMAP.md](ROADMAP.md)

将来的にはAndroid専用に留まらず、PWAやWebhookなど複数の入力元・送信先（WordPress以外にGitHub、Notion、Slackなど）に対応できる、AI時代のニュース収集基盤としての拡張を見据えています。
