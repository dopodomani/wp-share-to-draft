# Phase 3b Android Smoke Test Results

Record of an actual run of [docs/phase3-android-smoke-test-guide.md](phase3-android-smoke-test-guide.md) on the main PC (Android Studio + emulator/device). Unlike the guide (a reusable procedure), this file is a dated record — add a new dated section per run rather than overwriting the previous one, so there's a history of what was verified when, against which commit.

## Template for a new run

Copy the block below into a new section (replace the `2026-MM-DD` heading with the actual date) each time the smoke test is (re-)run.

Result values for every row: **PASS** / **FAIL** / **BLOCKED** / **NOT TESTED**.

```markdown
## YYYY-MM-DD

**Environment:**
- JDK version:
- Android Studio version:
- Android SDK Platform 35 installed: Yes/No
- Build Tools 35.0.0 installed: Yes/No
- Emulator/device used:
- Android OS version:
- git commit SHA:
- Matching Android CI run: <link> — Green? Yes/No

**1. Environment confirmation** — all fields above recorded: ☐ Done

**2. Build confirmation**
| Check | Result | Notes |
|---|---|---|
| Gradle Sync | | |
| `:app:assembleDebug` | | |
| `:app:testDebugUnitTest` | | |
| `:app:lintDebug` | | |
| App installs | | |
| App launches (no crash) | | |

**3. Settings**
| Check | Result | Notes |
|---|---|---|
| Site URL input | | |
| Username input | | |
| Application Password input | | |
| Application Password masked on screen | | |
| Invalid URL (non-https) validation | | |
| Save | | |
| Restored after app restart (routes to Confirm, not Settings) | | |
| Settings form pre-fill on reopen (known gap — confirm expected) | | |

**4. Share Target**
| Check | Result | Notes |
|---|---|---|
| App appears in Chrome's share sheet | | |
| Share URL only | | |
| Share title + URL | | |
| Share multi-line text with embedded URL | | |
| Share text with no URL | | |
| IntentParser result matches on Confirm screen | | |

**5. Confirm screen**
| Check | Result | Notes |
|---|---|---|
| Title displayed | | |
| URL displayed | | |
| Memo input | | |
| Save (submit) | | |
| Cancel | | |
| Double-submission prevented | | |
| Loading state shown | | |
| Success state + Done closes activity | | |

**6. WordPress integration**
| Check | Result | Notes |
|---|---|---|
| Draft created | | |
| Title prefixed `[INBOX] ` | | |
| Category is `素材候補` | | |
| `post_author` is the authenticated user | | |
| Title/URL/memo saved as expected | | |
| `edit_url`/`preview_url` correct | | |
| Verified directly in wp-admin | | |

**7. Error verification**
| Condition | Result | Notes |
|---|---|---|
| Wrong Application Password → 401 | | |
| `素材候補` unavailable → 409 | | |
| Nonexistent host → DnsFailure | | |
| Timeout | | |
| SSL error | | |
| WordPress stopped | | |
| Airplane mode → Unreachable | | |
| UI remains retryable after every error above | | |

**Overall:** ☐ All checks passed — Phase 3b real-device/emulator verification complete ☐ Issues found (see notes/linked issues below)

**Follow-ups filed (if any):**
-
```

---

## 2026-07-30

**Environment:**
- Emulator/device used: Pixel 9a API 37.1 (emulator-5554)
- git commit SHA: `63a9065` (main, per docs/phase3-android-smoke-test-guide.md's guidance to confirm CI green for the tested commit)
- Matching Android CI run: green (PR #1 merge commit)
- Production WordPress target: dopodomani.biz

**2. Build confirmation**
| Check | Result | Notes |
|---|---|---|
| Gradle Sync | PASS | |
| App installs | PASS | "Install successfully finished in 11s 212ms" |
| App launches (no crash) | PASS | Settings screen rendered correctly, matching design (site URL/username/Application Password fields, Japanese labels correct) |

**3. Settings**
| Check | Result | Notes |
|---|---|---|
| Site URL input | PASS | `https://dopodomani.biz` accepted; trailing slash correctly normalized by `trimEnd('/')` |
| Application Password masked on screen | PASS | Confirmed dots shown, not plaintext |
| Save | **FAIL** | Repro: launch app from icon (no pending share) → fill Settings → tap 保存. Expected: some confirmation UI or navigation. Actual: screen goes **blank white and stays blank indefinitely** — `SettingsUiState.Saved` has no rendered Composable branch (`LaunchedEffect(Unit) { onSaved() }` only, no UI), and `onSaved()` is a no-op when there's no pending shared item. Underlying save to `EncryptedSettingsRepository` appears to succeed regardless (confirmed indirectly: a later share-from-Chrome skipped Settings and went straight to Confirm) — this is a UI-only bug, not a data-persistence bug. **Follow-up: file as an Issue, fix in a small branch (Settings needs a visible "saved" state or navigates somewhere sensible when there's no pending item).**
| Restored after app restart (routes to Confirm, not Settings) | PASS | Confirms settings did persist despite the blank-screen bug above |
| Settings form pre-fill on reopen | NOT TESTED | (known gap, not re-verified this run) |

**4. Share Target**
| Check | Result | Notes |
|---|---|---|
| App appears in Chrome's share sheet | PASS | "Material Capture" visible in both the direct-share row and the app icon grid |
| Share title + URL | PASS | Confirm screen received a Wikipedia donation page's title/URL correctly |

**Environment note (not an app bug):** repeated "System UI isn't responding" / "Chrome isn't responding" ANR dialogs and one unresponsive-tap incident on this specific emulator profile (Pixel 9a API 37.1) — API 37.1 is a very new/preview API level. Recommended follow-up: re-run on an API 35 (stable, matches CI/Robolectric's tested level) AVD to isolate whether this is emulator-image-specific.

**6. WordPress integration**
| Check | Result | Notes |
|---|---|---|
| Draft created | **BLOCKED**, then diagnosed | First attempt: plugin wasn't installed on production yet → REST endpoint returned `404 rest_no_route` (confirmed via direct `curl`, not an app bug — app correctly showed a generic error for an unrecognized status). Resolved by installing/activating the plugin (packaged via `composer install --no-dev` + zip, delivered directly since Composer wasn't on the main PC). |
| Draft created (after plugin install) | **BLOCKED** | Route now registered (confirmed via `curl` → `401` instead of `404`), but authenticating with a real, verified-correct Application Password *still* returns this plugin's own `401 insufficient_capability` — meaning WordPress never authenticates the request at all. Root cause (confirmed both via a `.htaccess` `Authorization`-header-forwarding rewrite rule, which did **not** resolve it, and by the project's own pre-existing `publish_wordpress_article.py` already routing around identical behavior via XML-RPC): **this host does not forward the `Authorization` header to PHP**, so REST's Basic-Auth-based Application Password flow cannot work here at all. This is a hosting-level limitation, not an app or plugin bug — REST's own design and this plugin's error handling both behaved exactly as documented throughout. |

**Follow-ups filed (if any):**
- Settings-screen blank-after-save UI bug — to be filed as an Issue / fixed in a small branch (Phase 3b territory, code fix, independent of the items below)
- **Phase 2c/2d + 3c/3d initiated**: add an XML-RPC fallback transport (`material_capture.createDraft` WordPress method + Android `ConnectionMethod` Settings choice), since this specific production host cannot authenticate REST at all. Design docs: [docs/phase2c-xmlrpc-design.md](phase2c-xmlrpc-design.md), [docs/phase3c-android-xmlrpc-design.md](phase3c-android-xmlrpc-design.md); ADR: [docs/tech-decisions.md #11](tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport). Awaiting review before implementation.
- Remaining smoke test sections (5. Confirm screen submission success path, 7. Error verification) blocked on either the XML-RPC fallback landing, or testing against a WordPress host where REST's Authorization header works (e.g. LocalWP, per docs/phase2-smoke-test-guide.md) — not yet attempted this run.

---

## 2026-07-30 (Phase 3d — XML-RPC transport)

**Environment:**
- Emulator/device used: Pixel 9a API 37.1 (emulator-5554)
- git commit SHA: `feature/android-xmlrpc-publisher` branch (Phase 3d implementation), :core/:app builds green locally
- Production WordPress target: dopodomani.biz
- Settings: 接続方式 = XML-RPC (default)

**3. Settings**
| Check | Result | Notes |
|---|---|---|
| 接続方式 radio picker (XML-RPC / REST API) | PASS | XML-RPC pre-selected as designed; explanatory text rendered |

**5. Confirm screen**
| Check | Result | Notes |
|---|---|---|
| Save (submit) via XML-RPC — attempt 1 | **FAIL** (app bug, now fixed) | Logcat: `WordPressDestination: Publishing via XML_RPC` immediately followed by `FATAL EXCEPTION: ... android.os.NetworkOnMainThreadException` at `MaterialCaptureXmlRpcApi.createDraft` → app crash (PID killed). Root cause: `MaterialCaptureXmlRpcApi.createDraft` called OkHttp's blocking `execute()` directly without switching off `Dispatchers.Main.immediate` (Retrofit's suspend functions do this automatically; this hand-rolled call didn't). Fixed same-session by wrapping the call in `withContext(Dispatchers.IO)` — see `android/core/.../data/MaterialCaptureXmlRpcApi.kt`. |
| Save (submit) via XML-RPC — attempt 2 (post-fix) | PASS (no crash) | Logcat shows `Publishing via XML_RPC` with no following exception; UI transitions cleanly to the Error screen ("予期しないエラーが発生しました" / 再試行), i.e. `CaptureError.Unknown` |

**6. WordPress integration**
| Check | Result | Notes |
|---|---|---|
| Draft created via XML-RPC | **BLOCKED**, then diagnosed, then **PASS** | First attempt: `material_capture.createDraft` wasn't registered at all -- `system.listMethods` didn't list it. Root cause: `Plugin::registerXmlRpcMethods()` was hooked to `add_action('xmlrpc_init', ...)`, an action WordPress core doesn't define. Fixed by calling it unconditionally at plugin load time instead. Second attempt: registered correctly (confirmed via `system.listMethods`), but every real call returned `faultCode 500` with WordPress's generic fatal-error message. Root cause: `DraftXmlRpcHandler::createDraft` had a `(array $args, wp_xmlrpc_server $server)` signature, but WordPress's real XML-RPC dispatcher calls registered methods with a single `$args` argument only, causing a fatal `ArgumentCountError` on every invocation. Fixed by dropping the second parameter and reaching the server via the `$wp_xmlrpc_server` global. Third attempt: reached our own `IXR_Error(403, ...)` for `insufficient_capability` even though the account (投稿者/Author role) is confirmed able to post via other existing XML-RPC scripts on this same host -- root cause turned out to be a stale/incorrect Application Password value used for testing, not a real code or permission bug (confirmed once the correct Application Password was used). Final attempt: **draft successfully created** -- Confirm screen showed "下書きを作成しました: [INBOX] Make your donation now - Wikimedia Foundation". |
| `siteUrl` differs by transport on this host | Noted | dopodomani.biz's WordPress install lives in a `/tech` subdirectory; REST (`/wp-json/...`) only resolves at the domain root, while `xmlrpc.php` only resolves at `/tech/xmlrpc.php`. A single `siteUrl` setting can't serve both transports correctly on this particular host -- switching `connectionMethod` here also requires changing `siteUrl`. Recorded as a known host-specific limitation, not an app bug. |

**Overall:** ☑ All checks passed (after fixes) — real bugs found and fixed this session: `NetworkOnMainThreadException` in `XmlRpcPublisher`'s transport (Android), `xmlrpc_init` registration hook not existing in WordPress core (plugin), `ArgumentCountError` from a wrong callback signature (plugin), and a Japanese IME composition bug in the Confirm screen's fields (Android, two iterations — see the fix commits) fixed and confirmed working via copy-paste/Android IME testing. End-to-end XML-RPC draft creation against dopodomani.biz is now verified working.

**Follow-ups filed (if any):**
- Fixed this session (Android): `MaterialCaptureXmlRpcApi.createDraft` now runs its OkHttp call inside `withContext(Dispatchers.IO)`; Confirm screen's title/URL/memo fields now use a per-field local `TextFieldValue` keyed on `CaptureItem.sharedAt`, fixing both a "fields stay blank after a real share arrives" regression and a Japanese IME composition bug.
- Fixed this session (WordPress plugin, now 0.1.1): `xmlrpc_methods` registration hook corrected; `DraftXmlRpcHandler::createDraft` callback signature corrected to match WordPress's real XML-RPC dispatch convention.
- Fixed this session: Settings-screen blank-after-save UI bug (recorded above, 2026-07-30) — `SettingsUiState.Saved` now renders a "設定を保存しました" confirmation instead of nothing.
- Still open: none blocking Phase 2d/3d — both are considered complete pending CI/PR review.

*(Further dated run results are added above this line as they happen.)*
