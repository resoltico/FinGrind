---
afad: "3.5"
version: "0.4.0"
domain: DEVELOPER
updated: "2026-04-10"
route:
  keywords: [fingrind, build, gradle, architecture, quality-gates, java26, modules, sqlite, coverage]
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
- [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md)
- [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md)
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
- [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md)
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

## Architecture

FinGrind is a five-module Gradle project with a narrow accounting center and explicit write boundaries:

```text
core/         Accounting vocabulary and invariants:
              money, journal lines, journal entries, reversal linkage,
              request provenance, committed provenance, posting identity.

runtime/      Runtime persistence seam and in-memory implementation:
              PostingFact, PostingFactStore, PostingCommitResult,
              InMemoryPostingFactStore.

application/  Explicit write boundary:
              PostEntryCommand, PostEntryResult, PostingRejection,
              PostingIdGenerator, PostingApplicationService.

sqlite/       Durable single-book adapter:
              one SQLite file per entity book, persisted through an in-process SQLite adapter
              backed by Java 26 FFM and a managed SQLite 3.53.0 runtime on controlled surfaces
              and the canonical `book_schema.sql`.

cli/          Agent-first JSON CLI:
              help/version/capabilities plus preflight-entry and post-entry.
```

The dependency graph is deliberately one-way:

```text
cli -> sqlite -> runtime -> core
cli -> application -> runtime -> core
cli -> core
application -> core
```

FinGrind is intentionally hard-break oriented right now:
- one SQLite file is one book for one entity
- one canonical current schema defines new books
- no migration framework or backward-compatibility layer exists yet
- preflight is side-effect free against a missing book
- commit is append-only and reversals are additive links, not in-place mutation

## Foundations

| Component | Version |
|:----------|:--------|
| Java | 26 |
| Gradle Wrapper | 9.4.1 |
| SQLite runtime | managed SQLite 3.53.0 in root Gradle, nested Jazzer, CI, and Docker; standalone JAR requires a managed path or compatible system library |
| Jackson Databind | 3.1.1 |
| JUnit Jupiter | 6.0.3 |
| Jazzer | 0.30.0 |
| PMD | 7.23.0 |

## Commands

Root verification and packaging:

```bash
java --version
./gradlew verifyManagedSqliteSource
./gradlew check
./gradlew coverage
./gradlew :cli:shadowJar
./check.sh
```

Nested Jazzer verification:

```bash
./gradlew -p jazzer test jazzerRegression
./gradlew -p jazzer cleanLocalFindings cleanLocalCorpus fuzzAllLocal -PjazzerMaxDuration=30s
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

Root Gradle tests and `:cli:run` enable Java native access explicitly, compile a managed SQLite
3.53.0 shared library from `third_party/sqlite/sqlite-amalgamation-3530000/`, inject that library
through `FINGRIND_SQLITE_LIBRARY`, and keep the packaged CLI JAR on the same
`Enable-Native-Access: ALL-UNNAMED` runtime contract.

`./check.sh` is the local full-stack gate. It runs:
- root `check`
- root `coverage`
- nested `./gradlew -p jazzer check`
- `:cli:shadowJar`
- shell syntax checks for release-surface scripts
- Docker smoke verification, including semantic JSON assertions for discovery and write responses

During Stage 1, `./check.sh` tracks root `Test` task progress through semantic `[GRADLE-TEST-PULSE]` lines with class-start, class-complete, and scheduled in-flight test-progress heartbeats instead of relying only on stale Gradle task banners.

During Stage 2, `./check.sh` tracks nested Jazzer support tests and regression replay through `[JAZZER-PULSE]` lines, including support-test heartbeats plus regression-target `phase=plan`, `regression-input`, and `phase=finish` markers.

The nested Jazzer build is intentionally self-sufficient: it verifies the vendored SQLite source,
compiles its own managed SQLite 3.53.0 shared library from `../third_party/sqlite/`, and injects
that path through `FINGRIND_SQLITE_LIBRARY` for its support tests, regression replay, and local
active fuzzing commands.

Shared Gradle plugins, managed-SQLite task types, and pulse listeners now live under
`gradle/build-logic`, and the nested Jazzer build imports both that included build and the root
version catalog. See [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md) for the ownership map and
architecture rationale.

The root `build.gradle.kts` is intentionally thin. Repository-wide formatting, coverage
aggregation, and root managed-SQLite wiring now live in convention plugins rather than in
root-script `subprojects {}` policy blocks.

## GitHub Workflows

The repository currently ships three workflow surfaces:
- `CI` runs on pushes and pull requests to `main`, and includes `Check` and `Docker smoke`.
- `Release` runs for `v*` tags or manual dispatch, builds the fat JAR, and publishes the GitHub release.
- `Container` runs for `v*` tags or manual dispatch, builds and smoke-tests the image, publishes GHCR tags, and prunes older package versions.

Those workflows now verify the managed SQLite CLI runtime explicitly through `capabilities`, and
the Docker smoke gate asserts the containerized runtime reports SQLite 3.53.0 from the managed
library path.

GitHub workflows do not run active fuzzing, standalone Jazzer support tests, or regression replay.
Jazzer remains a local-only verification surface through `./check.sh` and the nested `jazzer/`
build.

Operational protocols for those surfaces live in:
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)

## Build Stance

FinGrind deliberately keeps several boundaries sharp:
- SQLite is the only durable backend currently planned.
- One SQLite file is one book for one entity.
- There is no generic database-independence layer.
- There is one canonical current SQLite schema and no migration layer.
- The CLI never bypasses the application boundary.
- Caller-supplied request provenance is distinct from committed audit metadata.
- Deterministic rejections stay separate from malformed requests and runtime failures.
- Root verification and nested Jazzer verification stay separate builds.

## Reference Spine

Public API reference lives in:
- [DOC_00_Index.md](./DOC_00_Index.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_02_Application.md](./DOC_02_Application.md)
- [DOC_03_RuntimeAndAdapters.md](./DOC_03_RuntimeAndAdapters.md)
