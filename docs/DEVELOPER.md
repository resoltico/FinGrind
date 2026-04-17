---
afad: "3.5"
version: "0.15.0"
domain: DEVELOPER
updated: "2026-04-17"
route:
  keywords: [fingrind, build, gradle, architecture, protocol-catalog, quality-gates, java26, modules, sqlite, sqlite3mc, coverage]
  questions: ["how do I build fingrind", "what is the fingrind module architecture", "what quality gates does fingrind enforce", "where does fingrind own operation metadata"]
---

# Developer Reference

**Purpose**: Build, test, architecture, and workflow reference for FinGrind contributors.
**Prerequisites**: Java 26 active in the current shell from the OpenJDK 26 bundle installed via [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md). Docker active in the current shell when running `./check.sh`, as codified in [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md). No global Gradle install is required for repo work; use `./gradlew`.

Companion documents:
- [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md)
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md)
- [DEVELOPER_DOCUMENTATION.md](./DEVELOPER_DOCUMENTATION.md)
- [DEVELOPER_DISTRIBUTION.md](./DEVELOPER_DISTRIBUTION.md)
- [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md)
- [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md)
- [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md)
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
- [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md)
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

## Architecture

FinGrind is a five-module Gradle project with a narrow accounting center, a contract-owned public
surface, executor-owned services, and explicit adapter seams:

```text
core/         Accounting vocabulary and invariants:
              money, positive journal-line money, journal lines, journal entries, reversal linkage,
              request provenance, committed provenance, posting identity.

contract/     Public request, result, metadata, and machine-contract surface:
              ProtocolCatalog, OperationId, ProtocolOperation, ProtocolLimits, ProtocolOptions,
              MachineContract, administration/query/write DTOs, rejections, committed facts,
              ledger plans, assertions, plan journals.

executor/     Execution services plus storage seams:
              BookAdministrationService, DeclareAccountCommand, DeclaredAccount,
              BookQueryService, BookInspection, paged account and posting query models,
              PostingDraft, PostingRequest, PostingIdGenerator, UuidV7PostingIdGenerator,
              PostingApplicationService, LedgerPlanService,
              BookAdministrationSession, PostingBookSession, BookQuerySession,
              PostingValidationBook, LedgerPlanSession, PostingCommitResult.

sqlite/       Durable single-book adapter:
              one protected SQLite file per entity book, persisted through an in-process SQLite
              adapter backed by Java 26 FFM and a managed SQLite 3.53.0 / SQLite3 Multiple
              Ciphers 2.3.3 runtime on controlled surfaces, implementing the executor-owned
              administration, posting, query, and ledger-plan seams over the canonical strict-table
              `book_schema.sql`.

cli/          Agent-first JSON CLI:
              help/version/capabilities plus print-request-template, print-plan-template,
              generate-book-key-file, open-book, rekey-book, inspect-book, declare-account,
              list-accounts, get-posting, list-postings, account-balance, execute-plan,
              preflight-entry, and post-entry, with discovery payloads rendered from
              contract-owned protocol metadata.
```

The dependency graph is deliberately one-way:

```text
cli -> sqlite -> executor -> contract -> core
cli -> executor -> contract -> core
cli -> contract -> core
sqlite -> contract -> core
executor -> contract -> core
contract -> core
```

Contract owns public protocol metadata. `dev.erst.fingrind.contract.protocol.ProtocolCatalog` is
the registry for operation ids, display labels, aliases, output modes, command summaries, shared
pagination limits, hard book-model facts, preflight facts, currency facts, and plan operation
kinds. Executor code assembles and executes typed workflows from that registry, and CLI code
renders or routes those DTOs without reauthoring operation names.

The AI-agent-first workflow is now first-class:
- `print-plan-template` emits the accepted `execute-plan` request shape
- `execute-plan` runs ordered steps atomically against one book session
- assertions are part of the public contract rather than ad hoc CLI behavior
- every plan returns a durable per-step journal for agent continuation

FinGrind is intentionally hard-break oriented right now:
- one SQLite file is one book for one entity
- every book-bound command requires exactly one explicit passphrase source:
  `--book-key-file`, `--book-passphrase-stdin`, or `--book-passphrase-prompt`
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
| Docker runtime | Docker Desktop daemon plus `docker buildx` reachable through the active shell `docker` command; smoke and release verification use an anonymous `DOCKER_CONFIG` while targeting the active local Docker engine |
| SQLite runtime | managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 in public bundles, root Gradle, nested Jazzer, CI, and Docker; developer-only raw `java -jar` requires `FINGRIND_SQLITE_LIBRARY` pointing at the managed build |
| Jackson Databind | 3.1.1 |
| JUnit Jupiter | 6.0.3 |
| Jazzer | 0.30.0 |
| PMD | 7.23.0 |

## Java 26 Feature Policy

FinGrind uses stable modern Java aggressively where it improves clarity or removes glue:
- records and sealed result families for closed business outcomes
- pattern-switch and other modern switch forms for deterministic dispatch
- collection conveniences such as `List.getFirst()`
- the final Java 26 FFM API for the SQLite bridge

FinGrind does not enable preview or incubator features by default.
That is deliberate best practice, not conservatism-by-accident:
- most headline JDK 26 additions remain preview or incubator surfaces
- preview features add repo-wide build, tooling, and release coupling through `--enable-preview`
- they should be adopted only when they materially simplify the architecture, not just because a
  newer syntax exists

Current stance:
- stable Java 26 features are preferred immediately
- preview or incubator JDK 26 features stay off until there is a concrete architecture win worth
  the extra lifecycle cost

## Commands

Root verification and packaging:

```bash
java --version
./gradlew verifyManagedSqliteSource
./gradlew prepareManagedSqlite
./gradlew check
./gradlew coverage
./gradlew :cli:bundleCliArchive
./scripts/bundle-smoke.sh
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
`FINGRIND_SQLITE_LIBRARY`, and keep the packaged CLI surfaces on the same
`Enable-Native-Access: ALL-UNNAMED` runtime contract.

Public release verification now centers on the self-contained bundle archive, not the raw JAR.
`./gradlew :cli:bundleCliArchive` builds the archive, and `./scripts/bundle-smoke.sh` on
macOS/Linux or `./scripts/bundle-smoke.ps1` on Windows proves that the extracted bundle runs
without ambient Java or a preconfigured `FINGRIND_SQLITE_LIBRARY`. That smoke gate also verifies
the top-level archive bootstrap files and the trimmed `jlink` runtime-image contract.

For local developer-only raw-JAR verification, remember that `:cli:shadowJar` packages only the
Java surface. If you want that JAR to run, run `./gradlew prepareManagedSqlite` as well and point
`FINGRIND_SQLITE_LIBRARY` at
the resulting file under `build/managed-sqlite/`, for example:

```bash
export FINGRIND_SQLITE_LIBRARY="$(find "$PWD/build/managed-sqlite" -type f \( -name 'libsqlite3.dylib' -o -name 'libsqlite3.so.0' \) | head -n 1)"
java -jar cli/build/libs/fingrind.jar capabilities
```

`./check.sh` is the local full-stack gate. It runs:
- root `check`
- root `coverage`
- nested `./gradlew -p jazzer check`
- `:cli:bundleCliArchive`
- self-contained bundle smoke verification
- shell syntax checks for release-surface scripts
- Docker smoke verification, including semantic JSON assertions for discovery, explicit book lifecycle, and write responses

The Docker smoke stage now runs public-image operations through a temporary anonymous
`DOCKER_CONFIG` while targeting the active local Docker engine derived from the current context.
That keeps the gate aligned with the real Docker runtime without making public pulls depend on
Docker Desktop credential-helper state or a contributor's personal login configuration.
If that stage materializes protected-book key files, those fixtures must obey the same owner-only
filesystem rule as production (`0400` or `0600`) instead of weakening the runtime contract.
The Docker path also verifies its managed SQLite source integrity and trimmed private runtime so
bundle and container publication stay on the same public runtime contract.

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
The documented shell operator surface, including `./check.sh` and `jazzer/bin/*`, must also remain
compatible with stock macOS `/bin/bash` 3.2 under `set -u`; in particular, do not assume
empty-array `"${array[@]}"` expansion is safe there.
If wrapper shell logic or Jazzer topology changes, run at least one live `jazzer/bin/*` command in
addition to deterministic nested `check`.

Shared Gradle plugins, managed-SQLite task types, and pulse listeners now live under
`gradle/build-logic`, and the nested Jazzer build imports both that included build and the root
version catalog. See [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md) for the ownership map and
architecture rationale.

The root `build.gradle.kts` is intentionally thin. Repository-wide formatting, coverage
aggregation, and root managed-SQLite wiring now live in convention plugins rather than in
root-script `subprojects {}` policy blocks.

## GitHub Workflows

The repository currently ships four workflow surfaces:
- `CI` runs on pushes and pull requests to `main`, and includes `Check` and `Docker smoke`.
- `Release` runs for `v*` tags or manual dispatch, builds the self-contained bundle matrix, and publishes the GitHub release.
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
- Every book is protected at rest through SQLite3 Multiple Ciphers and exactly one explicit
  passphrase source.
- FinGrind supports key files, stdin, and interactive terminal prompts; it intentionally rejects
  plaintext CLI passphrase arguments, environment-variable passphrase transport, and SQLite URI
  `key=` / `hexkey=` secret transport.
- There is no generic database-independence layer.
- There is one canonical current SQLite schema and no migration layer.
- The CLI never bypasses the contract and executor boundary.
- Caller-supplied request provenance is distinct from committed audit metadata.
- Deterministic rejections stay separate from malformed requests and runtime failures.
- Root verification and nested Jazzer verification stay separate builds.

## Reference Spine

Public API reference lives in:
- [DOC_00_Index.md](./DOC_00_Index.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_02_Application.md](./DOC_02_Application.md)
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)
