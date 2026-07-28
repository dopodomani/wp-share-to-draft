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

*(Actual dated run results are added above this line as they happen — none recorded yet.)*
