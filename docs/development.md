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

GitHub Actions is the environment that compensates for the secondary PC lacking an Android SDK — it's the one place every check always runs, regardless of which PC produced the change. On push/PR:

- **WordPress:** `composer install`, PHPUnit, PHPCS
- **Android:** Gradle wrapper validation, JVM unit tests, ktlint, Detekt, Android Lint, `assembleDebug`
- **Docs:** Markdown lint, Mermaid syntax check (if tooling supports it)

This means code authored on the secondary PC (Android-SDK-dependent parts included) still gets a full build/test pass before merging, even though it couldn't be verified locally where it was written. CI setup itself is **not yet built** — tracked as a Phase 3b/5 task, not implied as already existing by this document.

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
