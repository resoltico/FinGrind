---
afad: "3.5"
version: "0.9.0"
domain: DEVELOPER
updated: "2026-04-14"
route:
  keywords: [fingrind, build, gradle, architecture, quality-gates, java26, modules, sqlite, sqlite3mc, coverage]
  questions: ["how do I build fingrind", "what is the fingrind module architecture", "what quality gates does fingrind enforce"]
---

# Developer Reference

**Purpose**: Build, test, architecture, and workflow reference for FinGrind contributors.
**Prerequisites**: Java 26 active in the current shell from the OpenJDK 26 bundle installed via [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md). Docker active in the current shell when running `./check.sh`, as codified in [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md). No global Gradle install is required for repo work; use `./gradlew`.

Companion documents:
- [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md)
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md)
- [DEVELOPER_DOCUMENTATION.md](./DEVELOPER_DOCUMENTATION.md)
- [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md)
- [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md)
- [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md)
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
- [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md)
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

## Architecture

FinGrind is a four-module Gradle project with a narrow accounting center, an application-owned
book-session seam, and explicit write boundaries:

```text
core/         Accounting vocabulary and invariants:
              money, journal lines, journal entries, reversal linkage,
              request provenance, committed provenance, posting identity.

application/  Explicit write boundary plus persistence seam:
              BookAdministrationService, DeclareAccountCommand, DeclaredAccount,
              MachineContract and typed discovery descriptors,
              OpenBookResult, DeclareAccountResult, ListAccountsResult,
              PostEntryCommand, PostEntryResult, PostingRejection,
              PostingIdGenerator, UuidV7PostingIdGenerator,
              PostingApplicationService, BookSession, PostingFact,
              PostingCommitResult.

sqlite/       Durable single-book adapter:
              one protected SQLite file per entity book, persisted through an in-process SQLite
              adapter backed by Java 26 FFM and a managed SQLite 3.53.0 / SQLite3 Multiple
              Ciphers 2.3.3 runtime on controlled surfaces, implementing the application-owned
              `BookSession` seam over the canonical strict-table `book_schema.sql`.

cli/          Agent-first JSON CLI:
              help/version/capabilities plus open-book, declare-account,
              list-accounts, preflight-entry, and post-entry, with discovery
              payloads serialized from the application-owned machine contract.
```

The dependency graph is deliberately one-way:

```text
cli -> sqlite -> application -> core
cli -> application -> core
cli -> core
application -> core
```

FinGrind is intentionally hard-break oriented right now:
- one SQLite file is one book for one entity
- every book-bound command requires one explicit UTF-8 key file via `--book-key-file`
- book files are protected at rest with SQLite3 Multiple Ciphers 2.3.3 using the upstream default
  `chacha20` cipher
- one canonical current schema defines new books
- books are initialized explicitly before any posting
- preflight is advisory and not a durable commit guarantee
- one journal entry is exactly one currency
- every posting line references a declared active account
- the canonical book schema uses SQLite `STRICT` tables and opened handles disable `trusted_schema`
- no migration framework or backward-compatibility layer exists yet
- preflight is side-effect free against a missing book
- commit is append-only and reversals are additive links, not in-place mutation
- contributor verification belongs on the local filesystem; mounted external volumes are outside the
  supported setup because Gradle project-cache and JaCoCo file locking can fail there on macOS

## Foundations

| Component | Version |
|:----------|:--------|
| Java | 26 |
| Gradle Wrapper | 9.4.1 |
| Docker runtime | Docker Desktop daemon reachable through the active shell `docker` command; smoke and release verification use an anonymous `DOCKER_CONFIG` while targeting the active local Docker engine |
| SQLite runtime | managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 in root Gradle, nested Jazzer, CI, and Docker; standalone JAR requires a managed path or an exact compatible SQLite3MC system library |
| Jackson Databind | 3.1.1 |
| JUnit Jupiter | 6.0.3 |
| Jazzer | 0.30.0 |
| PMD | 7.23.0 |

## Commands

Root verification and packaging:

```bash
java --version
./gradlew verifyManagedSqliteSource
./gradlew prepareManagedSqlite
./gradlew check
./gradlew coverage
./gradlew :cli:shadowJar
./check.sh
```

Nested Jazzer verification:

```bash
./gradlew -p jazzer test
./gradlew -p jazzer jazzerRegression
./gradlew -p jazzer check
jazzer/bin/fuzz-cli-request -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-posting-workflow -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-sqlite-book-roundtrip -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-all -PjazzerMaxDuration=30s --console=plain
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

Coverage-gate protocol:
- never rely on JaCoCo defaults for verification semantics
- per-module verification must set both `LINE` and `BRANCH` counters explicitly
- per-module reports and verification must read all local `build/jacoco/*.exec` files, not only
  `test.exec`
- aggregated root coverage must read all subproject `build/jacoco/*.exec` files as well

This matters even when a repo currently has only the default Gradle `test` task: the moment a new
`Test` task appears, hardcoded `test.exec` assumptions become a silent coverage hole. See
[DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md) for the canonical build-logic protocol.

Root Gradle tests and `:cli:run` enable Java native access explicitly, compile a managed SQLite
3.53.0 / SQLite3 Multiple Ciphers 2.3.3 shared library from
`third_party/sqlite/sqlite3mc-amalgamation-2.3.3-sqlite-3530000/`, inject that library through
`FINGRIND_SQLITE_LIBRARY`, and keep the packaged CLI JAR on the same
`Enable-Native-Access: ALL-UNNAMED` runtime contract.

For local standalone JAR verification, remember that `:cli:shadowJar` packages only the Java
surface. If you want that JAR to run against the managed native library instead of a host
`libsqlite3`, run `./gradlew prepareManagedSqlite` as well and point `FINGRIND_SQLITE_LIBRARY` at
the resulting file under `build/managed-sqlite/`, for example:

```bash
export FINGRIND_SQLITE_LIBRARY="$(find "$PWD/build/managed-sqlite" -type f \( -name 'libsqlite3.dylib' -o -name 'libsqlite3.so.0' \) | head -n 1)"
java -jar cli/build/libs/fingrind.jar capabilities
```

`./check.sh` is the local full-stack gate. It runs:
- root `check`
- root `coverage`
- nested `./gradlew -p jazzer check`
- `:cli:shadowJar`
- shell syntax checks for release-surface scripts
- Docker smoke verification, including semantic JSON assertions for discovery, explicit book lifecycle, and write responses

The Docker smoke stage now runs public-image operations through a temporary anonymous
`DOCKER_CONFIG` while targeting the active local Docker engine derived from the current context.
That keeps the gate aligned with the real Docker runtime without making public pulls depend on
Docker Desktop credential-helper state or a contributor's personal login configuration.

During Stage 1, `./check.sh` tracks root `Test` task progress through semantic `[GRADLE-TEST-PULSE]` lines with class-start, class-complete, and scheduled in-flight test-progress heartbeats instead of relying only on stale Gradle task banners.

During Stage 2, `./check.sh` tracks nested Jazzer support tests and regression replay through `[JAZZER-PULSE]` lines, including support-test heartbeats plus regression-target `phase=plan`, `regression-input`, and `phase=finish` markers.

The nested Jazzer build is intentionally self-sufficient: it verifies the vendored SQLite3MC
source, compiles its own managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 shared library
from `../third_party/sqlite/`, and injects that path through `FINGRIND_SQLITE_LIBRARY` for its
support tests, regression replay, and local active fuzzing commands.

For active fuzzing, the supported operator surface is now `jazzer/bin/*`.
Those wrappers force active fuzz runs onto `--no-daemon`, serialize Jazzer commands through one
run lock, and own interrupt cleanup. Raw `./gradlew -p jazzer fuzz...` task names remain build
internals and are not the supported live-fuzz entrypoint.

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
- `Gradle wrapper validation` runs when wrapper files change and validates the checked-in wrapper surface.

Those workflows now verify the managed SQLite CLI runtime explicitly through `capabilities`, and
the Docker smoke gate asserts the containerized runtime reports SQLite 3.53.0, SQLite3 Multiple
Ciphers 2.3.3, required protected-book metadata, and wrong-key failure behavior from the managed
library path.

GitHub workflows do not run active fuzzing, standalone Jazzer support tests, or regression replay.
Jazzer remains a local-only verification surface through `./check.sh` and the nested `jazzer/`
build. Active harness execution also hard-fails when `GITHUB_ACTIONS=true`, so a future workflow
cannot silently become a live-fuzz surface by mistake.

Operational protocols for those surfaces live in:
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)

## Build Stance

FinGrind deliberately keeps several boundaries sharp:
- SQLite is the only durable backend currently planned.
- One SQLite file is one book for one entity.
- Every book is protected at rest through SQLite3 Multiple Ciphers and an explicit UTF-8 key file.
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
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)
