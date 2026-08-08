---
afad: "5.0.1"
version: "0.62.2"
domain: DEVELOPER_DOCUMENTATION
updated: "2026-08-09"
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
- `DOC_*.md`: AFAD-style reference atoms for exported main-source public API, the public CLI
  launcher entrypoint, and clearly marked normative next-format contracts, routed through
  `DOC_00_Index.md`
- other `docs/*.md`: auxiliary guides for users and contributors
- `docs/examples/`: runnable JSON, text, and CSV examples used by user guides

This split is intentional. User onboarding, developer operations, and API retrieval serve different
jobs and should not be collapsed into one giant file. When one reference area starts mixing
multiple domains into a retrieval-hostile god-file, split it back into narrower `DOC_*.md` files
and keep the old route only as a lightweight overview if compatibility is helpful.

## Placement Rules

Put new material in the narrowest fitting place:
- CLI usage, request flows, and copy-paste commands belong in user guides under `docs/`
- build, testing, storage, and workflow material belongs in developer guides under `docs/`
- public API state and normative next-format protocol contracts belong in `DOC_*.md`, not in
  narrative guides; an unreleased contract must say so explicitly and must never be presented as
  current CLI or persisted-format behavior
- nested public types in the reference spine use qualified symbol names such as `Outer.Inner`
- CLI and PDF adapter entrypoints belong in the reference spine too; do not bury exported adapter
  surfaces in narrative docs
- schema descriptions belong under `docs/sqlite/`
- example payloads and rendered output examples belong under `docs/examples/`

Do not put developer-only material into the root README.
Do not duplicate full API signatures into auxiliary guides.

## Maintenance Rules

When behavior changes, update the matching docs in the same change:
- public API changes: update `DOC_*.md`
- exported-symbol routing changes: update `DOC_00_Index.md` so every routed symbol points at a
  real file and a real `##` heading
- when one reference file grows past a clean domain boundary, split it and update
  `docs/README.md`, contributor guides that list the reference spine, and any old overview file
  that now serves only as a router
- keep the reference spine limited to main-source public surfaces and explicitly marked normative
  next-format contracts; test fixtures do not belong in `DOC_*.md`
- `docs/README.md` is the operator-facing documentation router and must list every checked-in
  top-level guide plus subdirectory schema/reference page that a contributor or operator is
  expected to discover from the docs tree
- CLI surface or JSON behavior changes: update user guides and any affected example payloads
- `docs/USER_CLI.md` keeps its command table in a generated block rendered from the canonical
  protocol catalog; update the catalog first and keep the generated block synchronized in the
  same change instead of hand-maintaining parallel command rows
- after changing protocol-owned command ids, aliases, option spellings, or summaries, rerun
  `./gradlew :contract:syncUserCliDocs` so the generated `docs/USER_CLI.md` block stays in exact
  canonical syntax, including raw `|`-separated option variants inside generated HTML code cells
- `docs/USER_INSTALL.md` and `docs/USER_QUICK_START.md` keep their bundle/publication matrices in
  generated blocks rendered from the canonical distribution and release-publication contracts;
  change those owners first and keep the generated install blocks synchronized in the same change
- after changing public bundle targets, bundle-layout metadata, release-publication tags, or
  platform declarations, rerun `./gradlew :contract:syncUserInstallDocs` so both generated user
  install blocks stay in exact canonical syntax
- public bundle user guides must not assume repo-local `docs/examples/` paths exist inside an
  extracted release archive; if a checked-in fixture is referenced, label it as source-checkout
  material and keep the runnable walkthrough pointed at bundle-safe local filenames instead
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
