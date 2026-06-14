# AGENTS.md — Agent Entry Protocol

**Version:** 3.8.0
**Updated:** 2026-06-13

Repository entry point for agent work. Defines load order, dispatch, precedence, repository-wide exceptions, and standing norms. Specialized rules live in the `.codex/` protocol stack; this file routes to them and states only what no other file owns.

## 0. Frame

You are a transient theory-holder: you enter the repository cold, build a partial theory of the slice you touch, act on it, and leave. The full discipline is the Universal Engineering Contract §0 (Naur, *Programming as Theory Building*). Its two standing obligations: surface tacit gaps rather than papering over them with confident output, and leave cues that help the next reader rebuild the relevant slice. A passing build, a closed issue, or a generated patch is not the outcome.

## 1. Context loading and dispatch

Read this file completely, then load top-to-bottom by trigger:

| Trigger | File | Min. version |
| --- | --- | --- |
| Always | `.codex/UNIVERSAL_ENGINEERING_CONTRACT.md` | 3.0.0 |
| File exists | `.codex/AGENTS_EXTRA.md` (project-specific instructions) | — |
| Java 26+ surface touched | `.codex/AGENTS_JAVA.md` | 2.2.0 |
| Kotlin 2.4+ surface touched | `.codex/AGENTS_KOTLIN.md` | 2.1.0 |
| Python 3.13+ surface touched | `.codex/AGENTS_PYTHON.md` | 2.1.0 |
| Rust 1.96+ / Cargo surface touched | `.codex/AGENTS_RUST_CARGO.md` | 2.2.0 |
| Tauri 2.11.x surface touched: apps, plugins, configuration, capabilities, permissions, bundling, updater/signing, mobile targets, frontend/Rust IPC | `.codex/AGENTS_TAURI.md` | 2.1.0 |
| SQLite surface touched: build, link, SQL, migrations, WAL, durability, bindings (baseline 3.53.2) | `.codex/AGENTS_SQLITE.md` | 1.0.0 |
| SQLite at-rest encryption touched: SQLite3 Multiple Ciphers ciphers, keys, rekey, key lifecycle (baseline 2.3.5) | `.codex/AGENTS_SQLITE3MC.md` | 1.0.0 |
| Gradle build logic touched | `.codex/PROTOCOL_GRADLE.md` | 1.0.0 |
| The change touches business meaning: the UEC §1.7 domain-meaning gate | `.codex/DOMAIN_DRIVEN_DESIGN_LENS.md` | 1.1.0 |
| CI, workflow, or pipeline configuration touched | `.codex/PROTOCOL_CI.md` | 1.0.0 |
| Documentation authoring or refactoring, or code changes that alter documented public contracts — unless the only touched document is the root `README.md` (§4) | `.codex/PROTOCOL_AFAD.md` | 5.0.0 |

Dispatch rules:

- Load one protocol per touched surface; multi-surface repositories load several. Framework, database, and build protocols stack on the language protocol: Tauri work also loads the Rust protocol plus applicable frontend norms; encrypted-SQLite work loads `AGENTS_SQLITE.md` first and `AGENTS_SQLITE3MC.md` on top of it, plus the relevant language protocol; a Gradle-built Java or Kotlin change loads both the language protocol and `PROTOCOL_GRADLE.md`, with the language protocol's build wiring taking precedence where it is stricter.
- The domain lens fires on business meaning, not on language. Touching Java does not mean DDD; touching the billing module of a Java app does. The trigger list is owned by UEC §1.7 — do not restate or re-derive it. Run the lens triage (lens §1) before applying tactical chapters, and never force the lens onto mechanical work.
- Surfaces with no protocol in this stack use the Universal Engineering Contract plus repository-specific instructions. Do not apply a protocol to an unrelated system unless the repository explicitly asks for it.
- Absent referenced file: continue with the best available context and state the missing file in the final report when it matters.
- A loaded file whose major version differs from its pin in the table: treat as a known re-cueing gap and surface it. Unpinned files are exempt.

## 2. Precedence

The most specific applicable instruction wins, but never silently relax correctness, security, compatibility, or verification requirements:

1. Explicit user request for the current task.
2. `.codex/AGENTS_EXTRA.md`.
3. This file, including the root `README.md` exception (§4).
4. Application-framework protocol.
5. Language/runtime protocol.
6. Database/native dependency protocol.
7. Build and automation protocols (Gradle, CI), which yield to the language protocol's build wiring where it is stricter.
8. Domain-modeling lens (when its gate fired).
9. Documentation protocol.
10. Universal Engineering Contract.
11. General language, framework, ecosystem, and documentation norms.

On conflict, prefer the stricter or more specific instruction unless that would make the task incorrect. Surface the conflict rather than guessing.

## 3. Before changing a system

For every non-trivial change:

- **System map.** Build the UEC §1 map — Truth, Evidence, Consequence, Invariant, Justification, Re-cueing — concretely enough that another agent could continue safely. When the UEC §1.7 gate fires, additionally apply the domain lens. Use the map to decide what to change, how far to widen the change, what to verify, what to document, and what to flag as unresolved.
- **Evidence over theorycrafting.** Base claims on the actual project: inspect code, tests, docs, examples, build files, configuration, scripts, and runtime behavior as needed. If a suspected issue cannot be proven, investigate further or mark it unconfirmed and state what evidence is missing.
- **In-progress work.** Inspect in-flight state, not just committed state:

  ```bash
  gh pr list --state open \
    --json number,title,url,headRefName,isDraft,author \
    --jq '.[] | [.number, .headRefName, .title] | @tsv'
  ```

  For any open PR overlapping the task area, read the body and the diff before proceeding. An open PR is theory-in-progress: continue from it, or explicitly explain why a fresh approach is better. The Truth axis includes everything in flight — discovering an open PR mid-task is late.

## 4. Documentation dispatch and the root README exception

`.codex/PROTOCOL_AFAD.md` governs agent-maintained documentation meant to stay synchronized with code, public APIs, architectural boundaries, operational procedures, or generated/reference material.

The root `README.md` is the front window of the store, never AFAD material:

- No AFAD frontmatter, symbol atoms, exhaustive API signatures, or schema tables.
- Optimize for a reader's first impression: what the project is, why it matters, how to install or run it, the shortest credible example, where to go next.
- Keep runnable snippets; prefer brevity over completeness. Link to AFAD-managed docs, guides, changelogs, or runbooks for detail.
- Preserve project-specific brand, tone, and release positioning unless asked to change them.

Nested `README.md` files are governed by their actual role: component and operational guides may use the documentation protocol; user-facing landing pages stay reader-first. `CHANGELOG.md`, `LICENSE`, `NOTICE`, `SECURITY.md`, `CONTRIBUTING.md`, governance, release-note, and legal files follow their own conventions unless `AGENTS_EXTRA.md` opts them into AFAD.

## 5. Standing working norms

These apply to every non-trivial session unless `AGENTS_EXTRA.md` overrides them. Session prompts may reference them by name or number.

### 5.1 Temporary workspace

Investigation tools, scripts, probes, fixtures, and experiments are encouraged, in any available runtime — including Ruby v4 (`ruby-brew`) and Python 3 (`python3`) — regardless of project language. Keep all temporary artifacts under `tmp/` at the project root, or the project's conventional scratch space. They must not interfere with quality gates, must not require configuration changes to hide them from checks, and must be deleted before final gate execution unless intentionally promoted into real tests, fixtures, tools, or documentation.

### 5.2 Incidental observations

Do not ignore unrelated deficiencies discovered while reading the project. Incorporate them into the session workplan when cohesive; defer to the project's observation log when truly out of scope; never silently skip. Do not derail the active task for an observation unless it blocks correctness or safety. If `OBSERVATIONS_INCIDENTAL.txt` (or the project's equivalent log) exists, read it and resolve every valid open item. The UEC's "next improvement is a separate slice" rule still applies.

A log entry records: stable ID; date; status; file and line range; category; what is wrong and why it matters; current pattern or excerpt; resolving change; effort level. Update resolved entries in place rather than deleting them.

### 5.3 Systems over goals

Fix root causes, not symptoms. Choose clean, decisive architecture over compatibility-preserving compromises, including breaking refactors when they are the correct engineering answer. Do not add backwards-compatibility layers, migration shims, transitional APIs, or legacy-preserving glue unless genuinely unavoidable — and then defend the shim with proof: name the consumer, the contract, and the removal trigger. Treat shims and migrations as technical debt. Break up god-files when encountered.

### 5.4 Project baseline

Apply the project's specified language, runtime, framework, and platform baseline when modernizing or refactoring. Do not assume a baseline the project does not specify, and do not silently raise one.

### 5.5 Tests assert intended behavior

Tests must assert the corrected or newly intended behavior — never loosen assertions, broaden tolerances, or skip tests to accommodate broken behavior. For fuzzing, property, or randomized suites: update them where relevant; add or revise seeds without skewing the corpus toward only the discovered cases; run the relevant checks where feasible, including live fuzzing when the project supports it.

### 5.6 Quality gates

Run the project's full quality-gate suite at the end of non-trivial work and iterate until green. Use the project's standard check script when one exists; otherwise include the applicable build, test, lint, formatting, documentation, example, packaging, fuzz/property, publication-dry-run, metadata, and dependency-license checks. Never weaken, bypass, exclude, or reconfigure gates to obtain a pass.

### 5.7 Documentation and public-facing artifacts

When code, behavior, commands, examples, APIs, or workflows change, update the corresponding documentation, examples, and parity docs in the same change (root `README.md` per §4). When the project keeps a `CHANGELOG.md`, record user- or developer-visible changes under `UNRELEASED` (or its equivalent), written from the public reader's point of view. Never mention this file, the `.codex/` stack, session prompts, work specifications, or AI-agent context in any public-facing artifact: changelog, README, release notes, examples, error messages, or help text. The same rule applies inside the code: comments, doc comments, and commit messages must not cite agent directive files by name or section as justification — state the self-contained engineering reason instead (write "No default arm: the compiler enforces exhaustiveness", never "Per AGENTS.md").

### 5.8 No emoji

No emoji anywhere: source code, comments, docstrings, commit messages, changelogs, configuration, documentation, plain text, this file. No exceptions. Remove emoji encountered while editing.

## 6. Final report

For non-trivial work, the final report combines:

- the UEC §9 output template — Truth, Evidence, Consequence, Invariant, Justification, Re-cueing — plus the domain-design block (lens §11) when the §1.7 gate fired;
- the operational record: what was done; breaking refactors performed; tests, fuzzing, examples, docs, and changelog updates; quality-gate commands run and their final results; genuinely blocked items, with precise reasons.

Keep it proportional to risk — a tiny edit needs one sentence with verification. Silence on justification gaps and inexpressible-theory claims a theory you do not have.
