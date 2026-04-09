---
afad: "3.5"
version: "0.1.0-SNAPSHOT"
domain: DEVELOPER
updated: "2026-04-09"
route:
  keywords: [fingrind, build, gradle, architecture, quality-gates, java26, modules, workflows, coverage]
  questions: ["how do I build fingrind", "what is the fingrind module architecture", "what quality gates does fingrind enforce"]
---

# Developer Reference

**Purpose**: Build, test, architecture, and workflow reference for FinGrind contributors.
**Prerequisites**: Java 26 is auto-provisioned through Gradle toolchains for compilation, but local packaged-CLI and wrapper-driven runs should also resolve `java` to 26. See [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md).

Companion documents:
- [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md)
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md)
- [DEVELOPER_DOCUMENTATION.md](./DEVELOPER_DOCUMENTATION.md)
- [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md)
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
- [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md)
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

## Architecture

FinGrind is a five-module Gradle project with a narrow accounting center and downstream adapters:

```text
core/         Accounting vocabulary and invariants:
              money, journal lines, journal entries, correction linkage,
              provenance, posting identity.

runtime/      Runtime persistence seam and simple in-memory implementation:
              PostingFact, PostingFactStore, InMemoryPostingFactStore.

application/  Explicit write boundary:
              PostEntryCommand, PostEntryResult, PostingApplicationService.

sqlite/       Durable single-book adapter:
              one SQLite file per entity book, persisted through the pinned sqlite3 CLI surface.

cli/          Agent-first JSON CLI:
              help/version/capabilities plus preflight-entry and post-entry.
```

The dependency graph is deliberately one-way:

```text
cli -> sqlite -> runtime
cli -> application -> runtime
application -> core
runtime -> core
sqlite -> core
```

The CLI is an adapter, not the core. The application boundary owns write semantics, and the
SQLite adapter owns the durable edge.

## Foundations

| Component | Version |
|:----------|:--------|
| Java | 26 |
| Gradle Wrapper | 9.4.1 |
| SQLite | 3.51.3 |
| Jackson Databind | 3.1.1 |
| JUnit Jupiter | 6.0.3 |
| Jazzer | 0.30.0 |
| PMD | 7.23.0 |

## Commands

Root verification and packaging:

```bash
java --version
./scripts/ensure-sqlite.sh
./gradlew check
./gradlew coverage
./gradlew :cli:shadowJar
./check.sh
```

Nested Jazzer verification:

```bash
./gradlew -p jazzer test jazzerRegression
```

Local CLI usage from source:

```bash
./gradlew :cli:run --args="help"
./gradlew :cli:run --args="capabilities"
./gradlew :cli:run --args="version"
```

## Quality Gates

`./gradlew check` is the root CI gate. It runs:
- Spotless formatting checks
- Error Prone compile-time checks
- PMD on main and test sources
- unit tests
- JaCoCo coverage verification at 100% line and 100% branch coverage

Root Gradle tests and `:cli:run` use the repo-pinned SQLite toolchain through
`FINGRIND_SQLITE3_BINARY`, backed by `scripts/sqlite3.sh`.

`./check.sh` is the local full-stack gate. It runs:
- root `check`
- root `coverage`
- nested `./gradlew -p jazzer check`
- `:cli:shadowJar`
- shell syntax checks for release-surface scripts
- Docker smoke verification, including semantic JSON assertions for discovery and write responses

During Stage 1, `./check.sh` now tracks root `Test` task progress through semantic
`[GRADLE-TEST-PULSE]` lines with class-start, class-complete, and scheduled in-flight
test-progress heartbeats instead of relying only on stale Gradle task banners.

During Stage 2, `./check.sh` tracks nested Jazzer support tests and regression replay through
`[JAZZER-PULSE]` lines, including support-test heartbeats plus regression-target `phase=plan`,
`regression-input`, and `phase=finish` markers.

## GitHub Workflows

The repository currently ships three workflow surfaces:
- `CI` runs on pushes and pull requests to `main`, and includes `Check` and `Docker smoke`.
- `Release` runs for `v*` tags or manual dispatch, builds the fat JAR, and publishes the GitHub release.
- `Container` runs for `v*` tags or manual dispatch, builds and smoke-tests the image, publishes GHCR tags, and prunes older package versions.

Operational protocols for those surfaces live in:
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)

Jazzer verification remains local-only by design through `./check.sh`. GitHub CI stays lighter and
does not run standalone Jazzer support tests or regression replay.

## Build Stance

FinGrind deliberately keeps several boundaries sharp:
- SQLite is the only durable backend currently planned.
- One SQLite file is one book for one entity.
- There is no generic database-independence layer.
- The CLI never bypasses the application boundary.
- Root verification and nested Jazzer verification stay separate builds.

## Reference Spine

Public API reference lives in:
- [DOC_00_Index.md](./DOC_00_Index.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_02_Application.md](./DOC_02_Application.md)
- [DOC_03_RuntimeAndAdapters.md](./DOC_03_RuntimeAndAdapters.md)
