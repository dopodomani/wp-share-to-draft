# Development Environment

This project is developed across two machines and two AI coding assistants. This document describes the physical/tooling split; [docs/ai-development.md](ai-development.md) covers the Claude Code / Codex operating rules in detail.

## Main PC

**Installed:** Android Studio, Android SDK, Android Emulator, JDK, Gradle, PHP, Composer, Git, Claude Code, Codex.

**Role:**
- Full Android app builds (Compose / Activity / Navigation / Hilt integration)
- Share Target behavior verification
- Emulator and real-device testing
- APK generation
- WordPress smoke testing against a real instance (per [docs/phase2-smoke-test-guide.md](phase2-smoke-test-guide.md))
- Pre-release verification
- Anything that specifically requires the Android SDK (see [What works with and without the Android SDK](#what-works-with-and-without-the-android-sdk) below)

## Secondary PC

**Installed:** JDK, Gradle, PHP, Composer, Git, Claude Code, Codex. **Not required:** Android Studio, Android SDK.

**Role:**
- `domain` and `application` layer work (both Android and WordPress sides)
- ViewModels, API models, Retrofit interfaces, error mapping
- JVM unit tests (no Android SDK needed for these — see the matrix below)
- WordPress plugin development (PHP has no SDK dependency at all)
- Documentation updates, design review, code review
- Git operations
- Lightweight implementation work

Anything the secondary PC produces that touches Android-SDK-dependent code is **written but not locally verified there** — say so explicitly when reporting on such work (see [Reporting unverified work](#reporting-unverified-work)), rather than assuming it compiles.

## What works with and without the Android SDK

This table is the general policy; [docs/phase3-android-app-design.md §9](phase3-android-app-design.md#9-android-test-strategy) has the concrete per-class breakdown for the Android app specifically.

| Work | Needs Android SDK? | Where it can run |
|---|---|---|
| WordPress plugin (all of it — PHP has no Android dependency) | No | Either PC |
| Android `domain`/`data` layer (`:core` module — models, use cases, `Destination`/`SettingsRepository` interfaces, Retrofit/DTOs/error mapping) | No | Either PC |
| Android ViewModels + their unit tests | **Yes to build/run** (ViewModels live in `:app` for Hilt's `@HiltViewModel`) — but the test logic itself is Android-free and can be written/reviewed on either PC | Main PC or CI to actually run |
| `IntentParser` unit tests (Robolectric — needs `android.content.Intent` on the classpath) | **Yes** | Main PC or CI |
| `data/local` (`EncryptedSettingsRepository`) Robolectric tests | **Yes** | Main PC or CI |
| Compose UI, `AndroidManifest.xml`/intent-filter, Hilt `@AndroidEntryPoint` wiring — compiling the app module at all | **Yes** | Main PC or CI |
| Instrumented tests, emulator/device runs, APK builds | **Yes** | Main PC only |

## Git branch strategy

No direct implementation commits to `main`. One branch per feature, kept simple (this is a small personal-scale project — avoid over-branching):

```
main
  ├─ feature/phase3-android
  │   ├─ feature/android-intent-parser
  │   └─ feature/android-api-tests
  ├─ feature/android-network
  ├─ fix/ci-android-build
  └─ docs/development-workflow
```

A large phase (e.g. all of Phase 3) is carried on one parent feature branch; a smaller, independent piece of work (e.g. one class, one CI fix) gets its own short-lived branch. WIP commits are fine mid-task but get cleaned up before merging into the parent/`main`.

## Switching machines mid-task

**Before starting work on either PC:**
```bash
git status
git branch --show-current
git fetch origin
git pull --ff-only   # if the branch has upstream commits not yet local
```
Never overwrite or discard uncommitted changes found this way without understanding what they are first.

**Before switching to the other PC:**
```bash
git status
git add <files>
git commit
git push
```
Don't leave local, unpushed changes and switch machines — if a task must be paused mid-way, commit a meaningful intermediate commit (or an explicit WIP commit, squashed/cleaned up before the branch merges) rather than losing continuity.

## CI's role

GitHub Actions is the environment that compensates for the secondary PC lacking an Android SDK — it's the one place every check always runs, regardless of which PC produced the change.

**Android CI is built** ([.github/workflows/android-ci.yml](../.github/workflows/android-ci.yml), added to supplement Phase 3b's build verification). On every push to `main` and every pull request touching `android/**`, a single Ubuntu job:

1. Checks out the repository
2. Sets up JDK 17 (Temurin)
3. Sets up the Android SDK, then explicitly installs `platforms;android-35` and `build-tools;35.0.0` to match `compileSdk`/`targetSdk` in `android/app/build.gradle.kts` — not left to AGP's lazy auto-download, so the exact packages needed are guaranteed up front rather than depending on network timing mid-build
4. Validates the committed Gradle wrapper jar against Gradle's known-good checksums — kept as its own explicit step even though `gradle/actions/setup-gradle` (next) also runs; the two check different things (this repo's committed wrapper jar vs. the Gradle distribution `setup-gradle` itself downloads), so it isn't true redundancy, and either way it's cheap enough to keep for a clear, single-purpose pass/fail signal in the log
5. Sets up Gradle with dependency/build caching
6. `./gradlew :core:test` — `:core` unit tests
7. `./gradlew :core:ktlintCheck`
8. `./gradlew :app:testDebugUnitTest` — `:app` JVM unit tests, including the Robolectric-based `IntentParserTest`/`EncryptedSettingsRepositoryTest` that need the Android SDK to run at all
9. `./gradlew :app:lintDebug` — Android Lint
10. `./gradlew :app:assembleDebug` — the actual SDK-dependent compile/package step the secondary PC cannot perform

No secrets are used or required: the build needs no real WordPress URL, username, or Application Password (`:core`'s network tests run entirely against MockWebServer). Test reports are uploaded as a workflow artifact on every run, pass or fail.

**Explicitly out of scope for this workflow:** emulator/instrumented tests, real-device Share Target behavior, and real-WordPress smoke testing — those stay Phase 4 territory (see [docs/phase2-smoke-test-guide.md](phase2-smoke-test-guide.md) and [ROADMAP.md](../ROADMAP.md)'s Phase 4 gate), not something this CI attempts to replace.

**Not yet built:** WordPress CI (`composer install`, PHPUnit, PHPCS) and docs CI (Markdown lint, Mermaid syntax check) — still tracked as Phase 5 tasks, not implied as existing by this document.

## Reporting unverified work

Every task report distinguishes:

- Implemented
- Verified locally (which PC)
- Verified via GitHub Actions
- Verifiable only on the main PC (not yet verified)
- Not verified — authored on the secondary PC, Android-SDK-dependent
- Real-device verification needed
- Design docs updated
- Remaining work / follow-ups

Neither Claude Code nor Codex reports something as "done" when it couldn't actually check it — an honest "written, not yet verified — needs the main PC or CI" is the expected report, not an assumed pass.
