# Phase 3b Android Smoke Test Guide

**Purpose:** manually verify the already-implemented Android app (Phase 3b) on the main PC — Android Studio, an emulator or a real device — closing the gap the secondary PC and Android CI cannot: real UI rendering, the real Share Target sheet, a real Chrome share, and a real end-to-end submission to a real WordPress instance. This is a **procedure document, not a design or code change** — no app code is modified as part of this guide.

Record actual results in [docs/phase3-android-smoke-test-results.md](phase3-android-smoke-test-results.md), not here — this file is the reusable procedure; that one is the dated record of a specific run. For the general testing picture (what's automated vs. manual, WordPress side included), see [docs/testing.md](testing.md).

## Prerequisites

- **A real WordPress instance with `material-capture` active, over HTTPS.** Reuse the environment from [docs/phase2-smoke-test-guide.md](phase2-smoke-test-guide.md) — specifically **LocalWP with SSL enabled**, not `wp-env`, because the Android app's own Settings screen rejects any site URL that doesn't start with `https://` before a request is ever sent (see [docs/phase3-android-app-design.md §3](phase3-android-app-design.md#3-viewmodel-construction)). An Application Password must already exist (see that guide's §5).
- **Android Studio, an emulator (API 26+) or a physical device, Chrome installed.**
- **This repository checked out at a specific commit** — record its SHA (step 1 below) and confirm Android CI succeeded for that exact commit before relying on any other result in this run. A smoke test against a commit CI hasn't verified is testing an unknown quantity, not this project's actual `:app`.

## 1. Environment confirmation

Record every field below in the results doc before testing anything else.

| Field | How to check |
|---|---|
| JDK version | Android Studio → Settings → Build Tools → Gradle → "Gradle JDK" (expect 17, matching `android/app/build.gradle.kts`' `compileOptions`/`kotlinOptions`) |
| Android Studio version | Help → About |
| Android SDK Platform 35 installed | Settings → Languages & Frameworks → Android SDK → SDK Platforms tab, "Android 15.0 (\"35\")" checked |
| Build Tools 35.0.0 installed | Same screen, SDK Tools tab → Android SDK Build-Tools, version 35.0.0 checked |
| Emulator or device used | Device name/model (e.g. "Pixel 8 API 35 (emulator)" or a real device's model) |
| Android OS version | The emulator/device's actual Android version (e.g. Android 15) |
| git commit SHA | `git rev-parse HEAD` in the repository root |
| CI success confirmation | Open [the Android CI workflow runs](https://github.com/dopodomani/wp-share-to-draft/actions/workflows/android-ci.yml), find the run for this exact SHA (or the merge commit that included it), confirm it's green. **Note the run URL in the results doc.** If this SHA has no corresponding green run, either wait for one or explicitly note in the results doc that this run is testing an unverified commit. |

## 2. Build confirmation

| Step | Expected result |
|---|---|
| Open `android/` as a project in Android Studio | Project loads, no immediate error dialog |
| Gradle Sync | Completes without error (this is the first real proof-of-build on this machine beyond CI) |
| `./gradlew :app:assembleDebug` (or Studio's Build menu) | `BUILD SUCCESSFUL`, produces a debug APK |
| `./gradlew :app:testDebugUnitTest` | All tests pass, including the Robolectric-based `IntentParserTest`/`EncryptedSettingsRepositoryTest` (pinned to `@Config(sdk = [34])` — see [docs/phase3-android-app-design.md §9](phase3-android-app-design.md#9-android-test-strategy)) |
| `./gradlew :app:lintDebug` | No new lint errors (warnings acceptable per current baseline) |
| Install the APK on the emulator/device | Installs without error |
| Launch the app (tap the icon) | Opens to the Settings screen with no crash (per [docs/phase3-android-app-design.md §1](phase3-android-app-design.md#1-screen-transition-diagram): launching from the icon with no settings saved yet routes to Settings) |

## 3. Settings

| Check | Expected result |
|---|---|
| Enter the WordPress site URL (`https://...`, from your LocalWP site) | Field accepts input |
| Enter the username | Field accepts input |
| Enter the Application Password | Field accepts input, **displayed masked** (`PasswordVisualTransformation` — confirm dots/bullets shown, not plaintext) |
| Invalid URL validation | Enter a site URL **without** `https://` (e.g. `http://...` or a bare domain) and tap Save → expect the inline validation message ("サイトURLは`https://`で始まる必要があります"), **no save actually occurs** |
| Save (with a valid `https://` URL) | Navigates onward (to Confirm if a share was pending, otherwise nothing further to navigate to — see [docs/phase3-android-app-design.md §1](phase3-android-app-design.md#1-screen-transition-diagram)) |
| Restart the app, then share a page (see §4) | Routes **directly to Confirm**, skipping Settings — confirms `SettingsRepository.hasSettings()` correctly reports the saved settings persisted across an app restart |
| Reopen Settings from the app icon after restart | **Known current limitation, confirm it matches expectations rather than assuming it's a bug:** the Settings form does not pre-fill previously saved values — `SettingsViewModel` always starts from a blank `Editing` state (see [docs/phase3-android-app-design.md §3](phase3-android-app-design.md#3-viewmodel-construction)). Record whether this is acceptable as-is or worth filing as a follow-up. |

## 4. Share Target

| Check | Expected result |
|---|---|
| Open any article in Android Chrome, tap Share | The share sheet opens |
| App appears in the share sheet | "Material Capture" (or the configured `app_name`) is listed among share targets |
| Share a page where Chrome's share text is just the URL | App opens to Confirm (or Settings first, if not yet configured) with `url` populated |
| Share a page where Chrome populates both a title and a URL | Both `title` and `url` populated correctly on Confirm |
| Share content with multi-line text where a URL appears mid-text | The URL is extracted into the `url` field; the surrounding text (URL removed) appears in the app's internal `sharedText` — not directly visible on Confirm, but reflected in the eventual post body's shared-text section once submitted (§6) |
| Share text containing no URL at all | App still opens; `url` field is **empty and editable** (never a crash, never a silent failure — per [docs/phase3-android-app-design.md §2](phase3-android-app-design.md#2-share-target-flow)) |
| Compare against `IntentParserTest`'s covered cases | Confirm the real Chrome share intent shape matches what the unit tests assumed — if Chrome's actual `EXTRA_SUBJECT`/`EXTRA_TEXT` shape differs from what was tested, note it here as a finding, not just a pass/fail |

## 5. Confirm screen

| Check | Expected result |
|---|---|
| Title displayed | Matches (or is empty/editable if extraction found none) |
| URL displayed | Matches (or is empty/editable if extraction found none) |
| Memo input | Typing into the memo field updates it |
| Tap Save (with title and URL both non-blank) | Transitions to a loading indicator, then either Success or Error |
| Tap Cancel | Activity closes, nothing submitted |
| Double-submission prevention | Rapidly double-tap Save — confirm only **one** draft is created (the `Idle`→`Loading` no-op invariant from [docs/phase3-android-app-design.md §8](phase3-android-app-design.md#8-state-transitions-loading--success--error)); check wp-admin afterward to be sure, not just the UI |
| Loading state visible | A spinner (or equivalent) shows while the request is in flight |
| Success state | Shows the created draft's title and a "完了"/Done control; tapping it closes the activity |

## 6. WordPress integration (verify in wp-admin after a successful Confirm submission)

| Check | Expected result |
|---|---|
| Draft created | A new post exists with status `draft` |
| Title prefixed `[INBOX] ` | **Note:** this prefix is on the post *title*, not a category — the category is a separate field below. Don't conflate the two. |
| Category is `素材候補` | Not literally named "`[INBOX]`" — `素材候補` ("material candidate") is the category name per [docs/api-spec.md](api-spec.md); confirm the post is filed under it |
| `post_author` is the authenticated user | Check the post's author in wp-admin matches the account whose Application Password was used |
| Title / URL / memo saved as expected | Post body contains 元URL, 保存日時 (server time), 共有日時 (if `shared_at` was sent), 共有元, メモ, and any `shared_text` — per the exact template in [docs/api-spec.md](api-spec.md#post-creation-semantics) |
| Response's `edit_url`/`preview_url` correct | If the app surfaces either after Success, confirm the link actually opens the right post in wp-admin |
| Visible/correct in wp-admin | Open the post directly in wp-admin to cross-check everything above, not just trusting the app's own success screen |

## 7. Error verification

Each row: trigger the condition, confirm the app maps it to the message/action documented in [docs/phase3-android-app-design.md §7](phase3-android-app-design.md#7-error-handling), and confirm the UI is left in a **retryable** state (not stuck, not crashed).

| Condition | How to trigger | Expected `CaptureError` / behavior |
|---|---|---|
| Wrong Application Password | Enter an incorrect password in Settings, then submit | `401` → "認証に失敗しました。Application Passwordを確認してください", action opens Settings |
| `素材候補` category unavailable | In wp-admin, delete the `素材候補` category, then submit | `409 category_unavailable` → "素材候補カテゴリーが見つかりません...", Retry offered |
| Nonexistent host | Set the site URL to a domain that doesn't resolve (e.g. `https://this-does-not-exist.invalid`) | `Network.DnsFailure` → "サイトのURLが見つかりません。URLを確認してください", opens Settings |
| Timeout | Point the site URL at a host that accepts the connection but never responds (or use a firewall rule to drop packets), or throttle the emulator's network to simulate a hang | `Network.Timeout` → "接続がタイムアウトしました", Retry offered |
| SSL error | Point the site URL at a host with an invalid/self-signed/mismatched certificate | `Network.SslFailure` → "サイトの証明書を確認できませんでした" |
| WordPress stopped | Stop the LocalWP site, then submit | Some `Network.*` variant (likely `Unreachable` or `Timeout` depending on how the connection fails) → confirm whichever appears matches its documented message/action, and note exactly which one it was |
| Airplane mode | Enable airplane mode on the device/emulator, then submit | `Network.Unreachable` → "ネットワークに接続できません", Retry offered |
| **All of the above** | — | After each error, confirm the Confirm screen returns to a state where the user can either **Retry** or **Edit** and try again — never a dead end requiring the app to be force-closed |

## 8. Recording method

Copy the template in [docs/phase3-android-smoke-test-results.md](phase3-android-smoke-test-results.md) for each dated run. Every checklist item above is recorded as exactly one of:

- **PASS** — worked as documented
- **FAIL** — did not work as documented (describe what happened)
- **BLOCKED** — could not be tested (e.g. no way to trigger the condition in this environment)
- **NOT TESTED** — skipped this run

...alongside: date/time performed, environment (which device/emulator + OS version), the commit SHA under test, free-text notes, and a link to any filed issue for a FAIL/BLOCKED item.
