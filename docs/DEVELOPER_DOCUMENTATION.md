---
afad: "3.5"
version: "0.17.0"
domain: DEVELOPER_DOCUMENTATION
updated: "2026-04-17"
route:
  keywords: [documentation, afad, doc-spine, storefront-readme, docs-index, user-guides, reference-atoms, examples]
  questions: ["how is documentation organized in fingrind", "where should new docs go in fingrind", "how should api docs and user docs be split in fingrind"]
---

# Documentation Developer Reference

**Purpose**: Keep FinGrind documentation structure coherent as the repo grows.
**Prerequisites**: Familiarity with the docs index in [README.md](./README.md).

## Documentation Layout

FinGrind uses a deliberately split documentation model:
- root [README.md](../README.md): storefront, user-facing only
- [README.md](./README.md) inside `docs/`: documentation index
- `DOC_*.md`: AFAD-style reference atoms for current public API
- other `docs/*.md`: auxiliary guides for users and contributors
- `docs/examples/*.json`: runnable example payloads used by user guides

This split is intentional. User onboarding, developer operations, and API retrieval serve different
jobs and should not be collapsed into one giant file.

## Placement Rules

Put new material in the narrowest fitting place:
- CLI usage, request flows, and copy-paste commands belong in user guides under `docs/`
- build, testing, storage, and workflow material belongs in developer guides under `docs/`
- public API state belongs in `DOC_*.md`, not in narrative guides
- schema descriptions belong under `docs/sqlite/`
- example JSON payloads belong under `docs/examples/`

Do not put developer-only material into the root README.
Do not duplicate full API signatures into auxiliary guides.

## Maintenance Rules

When behavior changes, update the matching docs in the same change:
- public API changes: update `DOC_*.md`
- CLI surface or JSON behavior changes: update user guides and any affected example payloads
- tooling or workflow changes: update developer guides
- schema changes: update `docs/sqlite/` and any affected rationale docs

Keep examples runnable as shown.
If a guide references a command or response envelope, verify it against the live CLI before closing the change.
Command references are also contract-linted: docs that invoke `fingrind` must use operation ids
registered in the contract protocol catalog, and backticked hyphen identifiers must be either
registered operations or known non-operation ids.

## Source Protocol

The repository's documentation protocol lives outside `docs/` in
[`../.codex/PROTOCOL_AFAD.md`](../.codex/PROTOCOL_AFAD.md).

That protocol governs:
- frontmatter shape
- reference-atom structure
- chunk-size discipline
- routing keywords and questions
- co-evolution expectations between code and docs
