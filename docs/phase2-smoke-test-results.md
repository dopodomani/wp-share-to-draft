# Phase 2b Smoke Test Results

Record of an actual run of [docs/phase2-smoke-test-guide.md](phase2-smoke-test-guide.md) against a real WordPress instance. Unlike the guide (a reusable procedure), this file is a dated record — add a new dated section per run rather than overwriting the previous one, so there's a history of what was verified when.

## Template for a new run

Copy the block below into a new section (replace the `2026-MM-DD` heading with the actual date) each time the smoke test is (re-)run — e.g. after a plugin code change, or a WordPress/PHP version bump.

```markdown
## YYYY-MM-DD

**Environment:**
- Tool: LocalWP / wp-env (Docker) — version:
- WordPress version:
- PHP version:
- Plugin version (from material-capture.php header):

**Setup / activation**
| Check | Result | Notes |
|---|---|---|
| Plugin activates with no fatal error or warning | ☐ Pass ☐ Fail | |
| `素材候補` category exists after activation | ☐ Pass ☐ Fail | |
| REST route is registered | ☐ Pass ☐ Fail | |

**Happy path (`201`)**
| Check | Result | Notes |
|---|---|---|
| Correct Application Password → `201 Created` | ☐ Pass ☐ Fail | |
| Title prefixed `[INBOX] ` | ☐ Pass ☐ Fail | |
| Status is `draft` | ☐ Pass ☐ Fail | |
| Author is the authenticated user | ☐ Pass ☐ Fail | |
| Assigned to `素材候補` | ☐ Pass ☐ Fail | |
| Body "保存日時" is server time (not `shared_at`) | ☐ Pass ☐ Fail | |
| Body "共有日時" matches request's `shared_at` | ☐ Pass ☐ Fail | |
| Body 元URL/共有元/メモ match the request | ☐ Pass ☐ Fail | |
| Response `edit_url`/`preview_url` present and open correctly | ☐ Pass ☐ Fail | |

**Error paths**
| Check | Expected | Result | Notes |
|---|---|---|---|
| No/invalid Application Password | `401` (WordPress's own shape) | ☐ Pass ☐ Fail | |
| Lacking `edit_posts` | `403 insufficient_capability` | ☐ Pass ☐ Fail | |
| Malformed `url` | `400 invalid_url` | ☐ Pass ☐ Fail | |
| Missing `title`/`url` | `400 missing_required_field` | ☐ Pass ☐ Fail | |
| Malformed `shared_at` | `400 invalid_shared_at` | ☐ Pass ☐ Fail | |
| `素材候補` deleted, then request repeated | `409 category_unavailable` | ☐ Pass ☐ Fail | |
| Plain HTTP request | `400 https_required` | ☐ Pass ☐ Fail | |

**Overall:** ☐ All checks passed — Phase 2b Definition of Done satisfied ☐ Issues found (see notes/linked issues below)

**Follow-ups filed (if any):**
-
```

---

*(Actual dated run results are added above this line as they happen — none recorded yet.)*
