# Changelog

All notable changes to this project are documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added
- Phase 1 design docs: architecture, API spec, tech decisions, security policy, roadmap.
- Per-phase Definition of Done and a design-review-gate process in ROADMAP.md.
- Phase 2 detailed design for the WordPress plugin (docs/phase2-wordpress-plugin-design.md), pending review.
- Phase 2 design decisions locked: PHP 8.1 minimum, Brain\Monkey + Mockery test roles, 素材候補 as standard category taxonomy.
- Phase 4 split into a design sub-stage (4a) gating execution (4b), matching Phases 2/3.

- `material-capture` WordPress plugin implemented (Domain/Application/Infrastructure/Rest), with a 39-test PHPUnit suite (Brain\Monkey + Mockery) and a clean, justified-customization PHPCS pass.
- Phase 2b smoke test guide and dated results template (docs/phase2-smoke-test-guide.md, docs/phase2-smoke-test-results.md) for verifying the plugin against a real WordPress instance (LocalWP or `wp-env`).

### Changed
- Phase 2 design revised after design review: split `Support/` into `Application/` (orchestration + ports) and `Infrastructure/` (WordPress adapters); made `Domain/DraftPayload` fully dependency-free; Mockery targets are now interfaces only; category lifecycle is create-once-at-activation and never-delete; `InputSanitizer` testing split into unit (delegation + own logic) vs. integration (real sanitization); API response/error shapes and field limits made concrete (see docs/phase2-wordpress-plugin-design.md and docs/api-spec.md for details).
