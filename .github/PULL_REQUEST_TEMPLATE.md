## What & why

<!-- What does this change, and why. Link the relevant ROADMAP.md item or issue if applicable. -->

## Docs

- [ ] If this changes a design (API shape, architecture, security posture), the relevant `docs/` file and `README.md` were updated in this same PR, per [CLAUDE.md](../CLAUDE.md#documentation) — not a follow-up.
- [ ] If this is a notable user-facing or design change, `CHANGELOG.md` was updated.

## Testing

- [ ] `wordpress-plugin`: `composer test` and `composer lint` pass locally.
- [ ] `android`: `:core:test` / `:app:testDebugUnitTest` and `ktlintCheck` pass locally.
- [ ] Manually verified where unit tests can't reach (real device, real WordPress) — describe what was checked, if anything.

## Commit style

- [ ] Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
