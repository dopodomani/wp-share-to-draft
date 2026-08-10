# AI-Assisted Development: Claude Code / Codex Roles

This project is developed with two AI coding assistants working across two machines (see [docs/development.md](development.md) for the physical/tooling split). This document is the operating agreement between them, so that work stays coherent regardless of which tool or machine touched it last.

## Claude Code — primary role

Works with the whole repository in view; responsible for keeping design and implementation coherent across files and phases.

- Architecture and directory structure
- Multi-file implementation
- Refactoring
- Test structure/strategy
- README / ADR (`docs/tech-decisions.md`) / API spec / security doc maintenance
- Checking design ↔ implementation consistency
- Confirming each phase's Definition of Done (see [ROADMAP.md](../ROADMAP.md))
- Organizing commits into small, topical units (per [CLAUDE.md](../CLAUDE.md)'s Conventional Commits rule)
- Assessing blast radius before implementation starts

**Rule:** when a design change turns out to be necessary, Claude Code proposes the documentation change first and gets it reviewed — it does not modify code first and update docs afterward. This mirrors the process already established for both the WordPress plugin ([docs/phase2-wordpress-plugin-design.md](phase2-wordpress-plugin-design.md)) and the Android app ([docs/phase3-android-app-design.md](phase3-android-app-design.md)).

## Codex — secondary role

Works on narrower, well-scoped tasks: review, verification, and specific fixes rather than open-ended design work.

- Reviewing individual files
- Small bug fixes
- Adding tests
- Checking GitHub Actions runs and investigating CI failures
- Security-focused review passes
- Checking implementation against [docs/api-spec.md](api-spec.md) for drift
- Diffing docs against code for staleness
- PR review
- Post-implementation improvement suggestions
- Supplementary work from the secondary PC

**Rule:** Codex does not change existing design decisions unilaterally. If Codex identifies a reason to change one, it presents:
- The reason for the change
- The blast radius (what else is affected)
- Alternatives considered
- Which docs would need updating, and how

...and stops there for a decision, the same way Claude Code does when it hits an ambiguous point (see [CLAUDE.md](../CLAUDE.md): "don't guess — present it as an explicit choice").

## Division of labor in practice

Before either tool starts work, the task's scope is stated explicitly so the two don't collide. Example split for a given session:

```text
Claude Code:
  - Phase 3 Android implementation as a whole
  - Layer structure
  - Hilt wiring
  - Keeping docs in sync with code

Codex:
  - IntentParser review
  - Adding MockWebServer tests
  - Fixing a CI failure
  - Checking API response shapes match docs/api-spec.md
```

**Neither tool edits the same area of the same branch at the same time.** Before starting, both check:

```bash
git status
git branch --show-current
git fetch origin
```

and `git pull --ff-only` if the branch has commits neither tool's local copy has yet. If uncommitted changes are found (from the other tool, the other machine, or the human), they are not overwritten or discarded without first understanding what they are — see the git-safety rules already in effect for this project (never `git reset --hard`/`git clean` over unknown local state without checking first).

**One branch, one active editor at a time.** Two machines/two AI tools do not work on the same branch simultaneously — see [docs/development.md](development.md#git-branch-strategy) for how branches are scoped so this doesn't come up in normal use.

## Why this split

Claude Code's context spans the whole repo and its history of design decisions, which suits it to work that must stay consistent across many files (architecture, cross-cutting refactors, doc/code coherence). Codex's strength here is being handed a small, well-bounded task and executing or reviewing it without needing the full project history reloaded each time — suited to point fixes, isolated reviews, and CI triage. Neither role is about capability difference; it's about matching each tool's natural working mode to the kind of task at hand.
