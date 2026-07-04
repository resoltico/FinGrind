---
afad: "4.0"
version: "0.59.0"
domain: DEVELOPER
updated: "2026-07-04"
route:
  keywords: [fingrind, build, gradle, architecture, protocol-catalog, quality-gates, java26, modules, sqlite, sqlite3mc, coverage]
  questions: ["how do I build fingrind", "what is the fingrind module architecture", "what quality gates does fingrind enforce", "where does fingrind own operation metadata"]
---

# Developer Reference

**Purpose**: Build, test, architecture, and workflow reference for FinGrind contributors.
**Prerequisites**: Either the preferred committed devcontainer path from
[DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md), or the host-native Java 26 setup from
[DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md) plus Docker in the active shell as codified in
[DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md). Root verification also depends on a working
`python3` plus `python3 -m pip` surface so the pinned repo-owned `uv` launcher can bootstrap the
Python helper tools declared in [`requirements-python-tools.txt`](../requirements-python-tools.txt).
No global Gradle install is required for repo work; use `./gradlew`.

The preferred contributor path is the committed devcontainer in
[DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md). VS Code is one supported client, not the
mandatory environment owner; the canonical environment is the committed Dev Container Spec surface.
Host-native Java remains supported for contributors who explicitly need it, but the devcontainer is
the documented default.

Companion documents:
- [DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md)
- [DEVELOPER_AGGREGATES.md](./DEVELOPER_AGGREGATES.md)
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md)
- [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md)
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md)
- [DEVELOPER_DOCUMENTATION.md](./DEVELOPER_DOCUMENTATION.md)
- [DEVELOPER_DISTRIBUTION.md](./DEVELOPER_DISTRIBUTION.md)
- [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md)
- [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md)
- [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md)
- [DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md)
- [DEVELOPER_SECURITY.md](./DEVELOPER_SECURITY.md)
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
- [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md)
- [ADR_SQLITE_JOURNAL_MODE.md](./ADR_SQLITE_JOURNAL_MODE.md)
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

## Context-First Map

Before the module graph, the system is easiest to reason about through its semantic boundaries:
- Public bookkeeping protocol in `contract.bookkeeping`: published commands, read/report DTOs,
  and deterministic rejection vocabulary
- Public workflow protocol in `contract.workflow`: published `LedgerPlan` requests plus public
  `LedgerJournal*` and `LedgerPlanResult` outputs
- Runtime/discovery contract in `contract.discovery` and `contract.runtime`: machine-contract
  descriptors, runtime/distribution facts, and discovery/catalog metadata
- Local bookkeeping context in `core` + `executor.bookkeeping`: working model for declarations,
  committed postings, read criteria, and report views
- Local workflow context in `executor.workflow`: ordered steps, assertions, internal journals, and
  boundary-failure semantics
- Host/adaptor contexts in `cli`, `sqlite`, and `report-pdf`

The module graph below is the implementation projection of those contexts.

## Architecture

FinGrind is a six-module Gradle project with a narrow accounting center, a contract-owned public
surface, executor-owned services, and explicit adapter seams:

```text
core/         Accounting vocabulary and invariants:
              money, positive journal-line money, journal lines, journal entries, reversal linkage,
              request provenance, committed provenance, posting identity,
              CurrencyBalance and EffectiveDateRange.

contract/     Public contract module hosting multiple public protocol subcontexts:
              bookkeeping protocol DTOs, workflow protocol DTOs,
              ProtocolCatalog, OperationId, ProtocolOperation, ProtocolOptions,
              ProtocolInteractionLimits,
              ProtocolPostEntryFields, ProtocolDeclareAccountFields,
              MachineContract plus ContractDiscovery / ContractTemplates /
              ContractRequestShapes / ContractResponse descriptor namespaces,
              deterministic error vocabularies, runtime/distribution/storage descriptors, and
              machine-readable public facts.

executor/     Execution services plus storage seams:
              BookAdministrationService, application seams, and context translators:
              public published language enters here and is translated into the internal
              bookkeeping and workflow contexts before execution,
              BookReadService, BookInspection, paged account and posting query/report models,
              PostingDraft, PostingIdGenerator, UuidV7PostingIdGenerator,
              PostingApplicationService, LedgerPlanService,
              BookStore, AtomicBookStore, PostingValidationStore,
              BookLifecycleInspection, PostingCommitResult.

sqlite/       Durable single-book adapter:
              one protected SQLite file per accounting-entity book, persisted through an
              in-process SQLite
              adapter backed by Java 26 FFM and a managed SQLite 3.53.2 / SQLite3 Multiple
              Ciphers 2.3.5 runtime on controlled surfaces, implementing the executor-owned
              administration, posting, query, and ledger-plan seams over the canonical strict-table
              `book_schema.sql` through focused helpers for connection setup, book-state reading,
              single-row query support, posting reads, and durable writes.

report-pdf/   PDF artifact adapter:
              one focused Apache PDFBox-based renderer module that turns contract-owned reporting
              DTOs into explicit PDF files for office-worker workflows without leaking PDFBox or
              document-layout concerns into executor or CLI command parsing.

cli/          Agent-first JSON CLI:
              help/version/capabilities plus print-request-template, print-plan-template,
              generate-book-key-file, open-book, rekey-book, backup-book, restore-book,
              inspect-rekey-rollback, restore-rekey-rollback, delete-rekey-rollback,
              inspect-book, declare-account, list-accounts, get-posting,
              list-postings, account-balance, trial-balance, account-ledger, period-summary,
              execute-plan, preflight-entry, and post-entry, with discovery payloads rendered
              from contract-owned protocol metadata.
```

The dependency graph is deliberately one-way:

```text
cli -> sqlite -> executor -> contract -> core
cli -> executor -> contract -> core
cli -> report-pdf -> contract -> core
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

For the named bounded contexts and translation rules behind that module graph, use
[DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md). The short version is:
- `contract` hosts the published bookkeeping protocol, the published workflow protocol, and the
  runtime/discovery contract
- `executor.bookkeeping` owns the local bookkeeping model
- `executor.workflow` owns plan orchestration semantics
- `cli` and `sqlite` are host/adaptor layers that translate at the boundary

Repo-owned JSON contract snapshots back that typed public surface:
- `contract/src/main/resources/dev/erst/fingrind/contract/protocol/contract-schema-keys.json`
- `contract/src/main/resources/dev/erst/fingrind/contract/protocol/operation-id-contract.json`
- `contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json`
- `contract/src/main/resources/dev/erst/fingrind/contract/protocol/release-publication-contract.json`
- `contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json`
- `contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-module-discovery-contract.json`
- `contract/build/generated-resources/protocol/dev/erst/fingrind/contract/protocol/runtime-environment-contract.json`

Build logic, runtime loaders, shell verifiers, and distribution assembly must consume those shared
JSON contract resources instead of carrying private parsers or duplicated literals. In particular,
the shell-side operation-id map must come straight from `operation-id-contract.json`, with its
lower-camel lookup keys derived from the canonical enum inventory there rather than copied into a
second full registry.

The AI-agent-first workflow is now first-class:
- `print-plan-template` emits the accepted `execute-plan` request shape
- `execute-plan` runs ordered steps atomically against one book session
- assertions are part of the public contract rather than ad hoc CLI behavior
- every plan returns a durable per-step journal for agent continuation

FinGrind's current public model is:
- one SQLite file is one book for one accounting entity
- every book-bound command requires exactly one explicit passphrase source:
  `--book-key-file`, `--book-passphrase-stdin`, or `--book-passphrase-prompt`
- book files are protected at rest with SQLite3 Multiple Ciphers 2.3.5 using the upstream default
  `chacha20` cipher
- protected book files and same-directory SQLite sidecars are hardened to owner-only filesystem
  permissions during mutation-capable opens when the host platform exposes a supported security
  model
- one canonical current schema defines new books
- books are initialized explicitly before any posting
- preflight is advisory and not a durable commit guarantee
- one journal entry is exactly one currency
- declared accounts have immutable `accountType` and immutable declared taxonomy once first
  stored, while `normalBalance` is derived from `accountType` plus classification doctrine
- every posting line references a declared active account
- the canonical book schema uses SQLite `STRICT` tables and opened handles disable `trusted_schema`
- the current supported on-disk format is `25`, owned by `BookFormatContract`
- `inspect-book` publishes one explicit hard-break migration policy for the active format line:
  no in-place upgrade path, no older-format acceptance, and no newer-format acceptance
- FinGrind is in an alpha hard-break line, so schema evolution advances by replacing the current
  model and rejecting non-matching book formats instead of carrying compatibility shims
- maintenance workflows are explicit: `backup-book` exports one verified encrypted backup pair,
  `restore-book` verifies that pair before replacing a live book path and the restored live book
  then reuses the backup pair's key file, `inspect-rekey-rollback` reports stale same-directory
  rollback artifacts, `restore-rekey-rollback` rewinds one interrupted rekey from one selected
  rollback artifact, and `delete-rekey-rollback` removes one stale rollback artifact without
  touching the live book path after verifying one initialized live book with one explicit
  passphrase source
- preflight is side-effect free against a missing book
- commit is append-only and reversals are additive links, not in-place mutation
- bookkeeping audit events are append-only durable facts in the same protected book
- `./gradlew` relocates per-checkout project cache, included build output, JaCoCo execution
  data, and ordinary project `build/` trees outside the checkout automatically, so source roots do
  not double as transient build caches; local disk remains the fastest path, but mounted
  filesystems no longer get special-case build-tree behavior

## Repo Hygiene

Repository-root policy:
- the checkout root is reserved for first-class project structure plus explicit ignored local-state
  roots such as `tmp/`, `.gradle/`, and other declared developer-tool directories
- temporary investigation state belongs under `tmp/`, not beside product modules or root docs
- unexpected top-level entries such as ad hoc date directories, stray flag-shaped names, or root
  `.DS_Store` files are verification failures

Canonical commands:
- `./scripts/verify-repo-hygiene.sh` verifies the root boundary and can print a local-state size
  report with `--report-local-state`; the report now classifies each local-state root as generated
  state, tool state, or scratch state and tells you which cleanup flag removes it; the verifier
  also fails when Git coordination lock files are present, because release or staging work cannot
  trust a checkout with an active or orphaned Git owner, and when a persisted `.git/gc.log` shows
  that Git housekeeping is suspended pending manual cleanup; it verifies repo-owned Git refs before running
  `git fsck --no-references` when the local Git supports that switch so tool-private ref namespaces do not masquerade as repository corruption
- `./scripts/clean-repo-hygiene.sh` removes empty unexpected root entries and Finder droppings; use
  `--purge-generated-state` to prune repo-owned generated caches, `--purge-tool-state` to discard
  ignored tool/editor state such as `.claude/` or `.vscode/`, and `--purge-tmp` when you want to
  clear the scratch tree explicitly

Generated-state stance:
- wrapper-owned Gradle project caches, JaCoCo data, included-build output, and ordinary build trees
  belong outside the checkout by default
- some explicitly mirrored consumer surfaces such as distribution or Docker context artifacts may
  still appear under owned module paths when their contract requires a checked-out path
- external tool state such as `.claude/` or `.vscode/` is ignored local state, not part of the
  repository source footprint

## Foundations

| Component | Version |
|:----------|:--------|
| Java | 26 |
| Python helper toolchain | Python 3.12 in CI, `uv` 0.11.25 as the repo-owned runner, plus helper-tool pins from `requirements-python-tools.txt` |
| Gradle Wrapper | 9.6.1 |
| Kotlin build logic | 2.4.0 in `gradle/build-logic`, emitting JVM 26 bytecode |
| Docker runtime | Docker Desktop daemon plus `docker buildx` reachable through the active shell `docker` command; smoke and release verification use an anonymous `DOCKER_CONFIG` while targeting the active local Docker engine |
| SQLite runtime | managed SQLite 3.53.2 / SQLite3 Multiple Ciphers 2.3.5 in public bundles, the published container image, the source-checkout wrapper, root Gradle, nested Jazzer, and CI; the developer direct-Java wrappers resolve that managed runtime only from a prepared checkout |
| Jackson Databind | 3.2.0 |
| JUnit Jupiter | 6.1.1 |
| Jazzer | 0.30.0 |
| JaCoCo | stable `0.8.15`, pinned in the shared version catalog and verified against the published Maven Central GA jars before Gradle quality gates run |
| PMD | 7.25.0 |

The build-logic Kotlin pin is now the stable `2.4.0` line.

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

Jackson dependency policy:
- declare only tools.jackson.core:jackson-databind directly
- do not add a separate direct jackson-annotations dependency
- Jackson 3 intentionally still resolves the annotation artifact and source namespace from
  com.fasterxml.jackson.annotation through its BOM, so those imports are expected here
- the current annotation namespace used in source is inherited from the approved databind
  entrypoint and is enforced by the Gradle verification policy plus round-trip regression tests,
  not by ad hoc per-module choices

## Commands

Root verification and packaging:

```bash
java --version
python3 -m pip install --user uv==0.11.25
./gradlew verifyManagedSqliteSource
./gradlew ruff sqlfluff
./gradlew prepareManagedSqlite
./gradlew check
./gradlew coverage
./gradlew :cli:bundleCliArchive
./scripts/bundle-smoke.sh
./check.sh
```

The canonical shell gates resolve the helper-tool Python runtime automatically. If the ambient
`python3` is older than the repo minimum, `./check.sh` and `./scripts/run-quality-gates.sh` fall
back to a `uv`-managed Python `3.12+` interpreter for `ruff` and `sqlfluff`.

Nested Jazzer verification:

```bash
jazzer/bin/test --console=plain
jazzer/bin/regression --console=plain
jazzer/bin/check --console=plain
jazzer/bin/fuzz-cli-request -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-ledger-plan-request -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-posting-workflow -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-sqlite-book-roundtrip -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-all -PjazzerMaxDuration=30s --console=plain
jazzer/bin/replay cli-request jazzer/.local/runs/cli-request/crash-<sha1> --console=plain
jazzer/bin/list-findings cli-request --console=plain
jazzer/bin/seed-audit --console=plain
jazzer/bin/promote-seed cli-request jazzer/.local/runs/cli-request/crash-<sha1> --name seed_name --intent "coverage intent" --console=plain
```

Committed-seed operators require lower_snake_case seed names, require each committed
`coverageIntent` to stay unique across the corpus, print supported replayable target keys on
their `--help` surfaces, and return structured deterministic `--json` failure payloads instead of
raw Gradle task boilerplate.

`jazzer/bin/fuzz-all` now stops on the first actionable harness failure and prints
replay-classified findings for that target before returning. Ordinary bounded Jazzer completions
now return success from the wrapper surface, while exit `124` is reserved for wrapper-enforced
timeout teardown when a harness does not stop after its fuzzing window.
`jazzer/bin/check` now drives the same nested `check` task that applies Spotless, Error Prone,
NullAway, PMD,
JaCoCo, and policy-task gate stack that the production Java modules use, and the deterministic
`jazzer/bin/test`, `jazzer/bin/regression`, and `jazzer/bin/check` entrypoints each start from a
clean relocated nested-build output so stale classfiles cannot poison coverage verification.

Local CLI usage from source:

```bash
./gradlew :cli:run --args="help"
./gradlew :cli:run --args="capabilities"
./gradlew :cli:run --args="version"
```

## Quality Gates

`./gradlew check` is the root CI gate. It runs:
- Spotless formatting checks
- Ruff lint and formatting checks for `scripts/**/*.py`
- SQLFluff verification for the canonical SQLite schema file
- Error Prone compile-time checks
- PMD on main and test sources
- unit tests
- JaCoCo coverage verification at 100% line and 100% branch coverage

Coverage-gate protocol:
- never rely on JaCoCo defaults for verification semantics
- per-module verification must enforce zero missed `LINE` and zero missed `BRANCH` counters from
  the generated `jacocoTestReport.xml` surface
- each `Test` task must reset its own JaCoCo `.exec` file before execution and may append within
  that one task run only, so coverage truth cannot inherit stale data from earlier sessions
- per-module reports and verification must read all local `build/jacoco/*.exec` files, not only
  `test.exec`
- aggregated root coverage must read all subproject `build/jacoco/*.exec` files as well

This matters even when a repo currently has only the default Gradle `test` task: the moment a new
`Test` task appears, hardcoded `test.exec` assumptions become a silent coverage hole. See
[DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md) for the canonical build-logic protocol.

Root Gradle verification and the explicit CLI/runtime task owners enable Java native access where
required, compile a managed SQLite 3.53.2 / SQLite3 Multiple Ciphers 2.3.5 shared library from
`third_party/sqlite/sqlite3mc-amalgamation-2.3.5-sqlite-3530200/`, and keep the packaged CLI
surfaces on the same managed-runtime contract. The source-checkout wrapper and developer
direct-Java wrappers discover that prepared checkout runtime without any operator override path
and now launch through the Gradle-owned Java 26 toolchain executable rather than ambient shell
Java.

Public release verification now centers on the self-contained bundle archive, not the raw JAR.
`./gradlew :cli:bundleCliArchive` builds the archive, and `./scripts/bundle-smoke.sh` on
macOS/Linux or `./scripts/bundle-smoke.ps1` on Windows proves that the extracted bundle runs
without ambient Java or any retired SQLite runtime override variables. That smoke gate also
verifies the top-level archive bootstrap files and the trimmed `jlink` runtime-image contract.
For published Linux classifiers, the same Bash owner also supports
`./scripts/bundle-smoke.sh --execution-surface compatibility-floor`, which reruns the office-worker
acceptance path inside the contract-declared Rocky Linux 9 minimum-glibc container before release
promotion.
The bundle task prints the exact archive path and checksum path it produced under the active build
directory so operators and agents can pick up the right artifact without guessing where Gradle
placed it. Stage 1 structural governance now scans tracked Markdown and tracked JSON resources
across the repository, which includes the `.codex` protocol manuals plus contract catalogs,
bundle-root payload examples, and other repo-owned JSON surfaces under explicit family budgets
instead of only the user-facing `docs/` tree.

For local developer-only raw-JAR verification, remember that `:cli:shadowJar` packages only the
Java surface. If you want that JAR to run from the checkout, prepare the managed runtime first:

```bash
./gradlew :cli:shadowJar prepareManagedSqlite
./scripts/direct-java-cli.sh capabilities
```

When that JAR is moved outside the prepared checkout layout, that launch shape is unsupported.

`./check.sh` is the local full-stack gate. It runs:
- root `check`
- root `coverage`
- `jazzer/bin/check`
- `:cli:bundleCliArchive`
- self-contained bundle smoke verification
- shell syntax checks for release-surface scripts
- Docker smoke verification, including semantic JSON assertions for discovery, explicit book lifecycle, and write responses

The committed contributor-devcontainer surface is a separate first-class contributor verification
entrypoint:

```bash
./scripts/validate-devcontainer.sh
```

The Docker smoke stage now runs public-image operations through a temporary anonymous
`DOCKER_CONFIG` while targeting the active local Docker engine derived from the current context.
That keeps the gate aligned with the real Docker runtime without making public pulls depend on
Docker Desktop credential-helper state or a contributor's personal login configuration.
If that stage materializes protected-book key files, those fixtures must obey the same owner-only
filesystem rule as production (`0400`/`0600` on POSIX filesystems, owner-only ACL on Windows)
instead of weakening the runtime contract.
The Docker path also verifies its managed SQLite source integrity and trimmed private runtime so
bundle and container publication stay on the same public runtime contract.

During Stage 1, `./check.sh` tracks root `Test` task progress through semantic `[GRADLE-TEST-PULSE]` lines with class-start, class-complete, and scheduled in-flight test-progress heartbeats instead of relying only on stale Gradle task banners.
That stage now runs through `./scripts/run-quality-gates.sh`, which pairs root `check coverage`
with the included `gradle/build-logic:test` surface so the canonical local gate and CI exercise
the repository's verification plugins as first-class code.

During Stage 2, `./check.sh` tracks nested Jazzer deterministic tests and regression replay
through `[JAZZER-PULSE]` lines, including deterministic-tests heartbeats plus
regression-target `event=plan`, `regression-input`, and `event=finish` markers.

The nested Jazzer build is intentionally self-sufficient: it verifies the vendored SQLite3MC
source, compiles its own managed SQLite 3.53.2 / SQLite3 Multiple Ciphers 2.3.5 shared library
from `../third_party/sqlite/`, writes the local-consistency `.sha256` file for that built
library, and resolves that managed
runtime from its prepared nested build layout for deterministic tests, regression replay, and
local active fuzzing commands.

For all Jazzer operations, the supported operator surface is now `jazzer/bin/*`.
Those wrappers serialize FinGrind verification through the shared repo lock and own the deterministic
and active-fuzz launch contract. Active fuzz runs force `--no-daemon` and own interrupt cleanup.
Raw `./gradlew -p jazzer ...` task names remain nested-build internals and are not the supported
Jazzer operator entrypoint. Wrapper target discovery is owned by `jazzerActiveTargets` plus
`jazzerReplayableTargets` only as derived Gradle projections; the committed Jazzer topology JSON is
the owner consumed by wrapper discovery, runtime support, and build logic.
The documented shell operator surface, including `./check.sh` and `jazzer/bin/*`, must also remain
compatible with stock macOS `/bin/bash` 3.2 under `set -u`; in particular, do not assume
empty-array `"${array[@]}"` expansion is safe there.
If wrapper shell logic or Jazzer topology changes, run at least one live `jazzer/bin/*` command in
addition to deterministic nested `check`.

Root `./check.sh`, `./scripts/docker-smoke.sh`, `./scripts/validate-devcontainer.sh`, and the
Jazzer wrappers now also share one repo-keyed cache-root `GRADLE_USER_HOME` plus the repo-wide
verification lock under the user cache root
(`${XDG_CACHE_HOME:-$HOME/.cache}/fingrind/repo-verification-locks` by default, keyed by
repository path). That isolation keeps full verification from sharing daemon or cache state
accidentally with sibling commands without perturbing Gradle's observed repository inputs or
forcing wrapper-owned lock files back into the checkout. Descendant shell processes reenter that
lock through published owner metadata in the lock directory, so monitored shell shapes such as
`bash ... > >(tee ...)` do not break wrapper reentry.

Shared Gradle plugins, managed-SQLite task types, and pulse listeners now live under
`gradle/build-logic`, and the nested Jazzer build imports both that included build and the root
version catalog. See [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md) for the ownership map and
architecture rationale.

The root `build.gradle.kts` is intentionally thin. Repository-wide formatting, coverage
aggregation, and root managed-SQLite wiring now live in convention plugins rather than in
root-script `subprojects {}` policy blocks.

## GitHub Workflows

The repository ships four workflow files and one release-blocking CI graph:

- `CI` runs on pushes, pull requests to `main`, and manual `workflow_dispatch`, and publishes the
  aggregate `Gate` required-status job plus `Check`, `Prepare published bundle smoke matrix`,
  one `Published bundle smoke (<classifier>)` job for each published bundle target, and the
  devcontainer pair.
- `Release` runs for `v*` tags or manual dispatch, builds the self-contained bundle matrix, and publishes the GitHub release.
- `Container` runs for `v*` tags or manual dispatch, builds and smoke-tests the image, publishes GHCR tags, and prunes older package versions.
- `Gradle wrapper validation` runs when wrapper files change and validates the checked-in wrapper surface.

**CI job structure:**

1. `check` — core Linux quality gate: runs `run-quality-gates.sh`, deterministic Jazzer
   regression, SQLite verification, bundle build and smoke, and release-surface script checks.
   Runs on `ubuntu-24.04`.
2. `prepare-published-bundle-smoke-matrix` — renders the published bundle matrix from the same
   canonical release-plan reader that tagged publication uses, so CI and release cannot drift on
   target ownership.
3. `published-bundle-smoke` — runs the release-owned publication-proof matrix after `check`
   passes. The matrix expands to every classifier whose publication status is `published` in
   `bundle-publication-contract.json`, currently `macos-aarch64`, `macos-x86_64`,
   `linux-x86_64`, `linux-aarch64`, and `windows-x86_64`. It verifies the native runner identity
   by normalizing live host spellings back to the canonical bundle target ids, proves the managed
   SQLite runtime surfaces, builds the exact published bundle classifier on each runner, reads the
   emitted archive/checksum paths from the Gradle-owned bundle manifest, and delegates archive
   acceptance to the canonical bundle-smoke owners. Linux targets rerun that acceptance flow on
   the contract-declared Rocky Linux 9 compatibility floor. The Windows leg also keeps the
   included build-logic tests plus the direct-Java and source-checkout runtime verifiers on
   `windows-2022`. Uses the repo-owned
   [configure-windows-defender-build-exclusions.ps1](../scripts/configure-windows-defender-build-exclusions.ps1)
   owner for one best-effort Windows Defender exclusion attempt on the workspace and Gradle user
   home before Gradle work begins. The exclusion attempt is a performance optimization only: an
   unavailable Defender service must warn and continue instead of blocking the product-verification
   lane.
4. `devcontainer-changes` — detection job that computes a git diff of the PR's changed files
   against the devcontainer trigger paths. Runs independently; no upstream dependency.
5. `devcontainer` — validates the committed contributor devcontainer surface through
   `./scripts/validate-devcontainer.sh`. Fires only when `devcontainer-changes` reports that a
   relevant file changed; skipped otherwise. No longer depends on `check` — the devcontainer
   environment is orthogonal to code correctness and should be proven whenever its files change
   regardless of whether the application gate passes.
6. `gate` — aggregate required-status job using `if: always()` with explicit `${{ toJSON(needs.*.result) }}` failure detection so a correctly skipped `devcontainer` gate does not prevent `Gate` from being reported or block merge; only a failed or cancelled job prevents success.
   It aggregates `check`, `prepare-published-bundle-smoke-matrix`, the published bundle-smoke matrix, and the devcontainer gate pair.
   Configure branch protection to require `Gate` as the single required check, code-owner review on the protected surfaces routed through `.github/CODEOWNERS`,
   and administrator bypass availability for the repository owner so the protected release/publication workflow is not deadlocked
   by a self-review requirement.

**Path-based devcontainer gate theory.** The devcontainer gate validates the contributor
*environment*, not application code. Application code changes are already proven by `check` and
the published bundle-smoke matrix. Running the full Docker build-and-validate cycle on every PR
regardless of what changed wastes 15-20 minutes per run. The gate therefore fires only when the
environment itself changes — specifically when any of these paths are touched:

- `.devcontainer/` — the Dockerfile and `devcontainer.json`
- `scripts/validate-devcontainer.sh`
- `scripts/devcontainer-prepare-user-home.sh`
- `scripts/repo-verification-lock-support.sh`
- `scripts/python-runtime-support.sh`

A `devcontainer-changes` detection job computes the diff before the gate is evaluated. When no
relevant files changed, `devcontainer` is skipped. A skipped result is a correct, intended
outcome, not a coverage gap.

All CI runners use pinned runner images (`ubuntu-24.04`, `windows-2022`) rather than the floating
`ubuntu-latest` / `windows-latest` labels, so runner image updates cannot silently change the
build environment between runs. The `workflow_dispatch:` trigger also lets maintainers manually
rerun the full aggregate `Gate` against a branch when GitHub fails to attach the `pull_request`
workflow on initial PR open.

Those workflows now verify the managed SQLite CLI runtime explicitly through `capabilities`, and
the Docker smoke gate asserts the containerized runtime reports SQLite 3.53.2, SQLite3 Multiple
Ciphers 2.3.5, required protected-book metadata, and wrong-key failure behavior from the managed
library path.

GitHub workflows do not run active fuzzing.
They now do run the deterministic Jazzer verification wrapper through `./jazzer/bin/check`, so
deterministic corpus replay is part of CI while active fuzzing remains local-only through
`./check.sh` and `jazzer/bin/*`. Active harness execution
also hard-fails when `GITHUB_ACTIONS=true`, so a future workflow cannot silently become a
live-fuzz surface by mistake.

Operational protocols for those surfaces live in:
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)

## Build Stance

FinGrind deliberately keeps several boundaries sharp:
- SQLite is the only durable backend currently planned.
- One SQLite file is one book for one accounting entity.
- Every book is protected at rest through SQLite3 Multiple Ciphers and exactly one explicit
  passphrase source.
- Rekeying preserves one rollback copy until the replacement secret is verified, so verification
  failures restore the pre-rekey file automatically instead of leaving an unverified rotation on
  disk; a crash can leave that encrypted rollback artifact behind until an operator reviews the
  warning emitted on the next open.
- FinGrind supports key files, stdin, and interactive terminal prompts; it intentionally rejects
  plaintext CLI passphrase arguments, environment-variable passphrase transport, and SQLite URI
  `key=` / `hexkey=` secret transport.
- There is no generic database-independence layer.
- There is one canonical current SQLite schema, with the supported format version owned by
  `BookFormatContract`.
- Alpha schema evolution uses one explicit hard-break migration policy for the active format line:
  there is no in-place upgrade path and non-matching book formats are rejected rather than routed
  through legacy-compatibility code.
- The CLI never bypasses the contract and executor boundary.
- Caller-supplied request provenance is distinct from committed audit metadata.
- Deterministic rejections stay separate from malformed requests and runtime failures.
- Root verification and nested Jazzer verification stay separate builds.

## Reference Spine

Public API reference lives in:
- [DOC_00_Index.md](./DOC_00_Index.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_01_Core_LedgerAndPosting.md](./DOC_01_Core_LedgerAndPosting.md)
- [DOC_02_Application.md](./DOC_02_Application.md)
- [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md)
- [DOC_02_AdministrationAndReports.md](./DOC_02_AdministrationAndReports.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)
- [DOC_04_CliAndPdfAdapters.md](./DOC_04_CliAndPdfAdapters.md)

That reference spine tracks main-source public surfaces plus the public CLI launcher entrypoint.
`DOC_02_Application.md` is now the routing overview for the split contract/executor reference set, and `DOC_00_Index.md` routes every exported symbol to the narrower file that actually owns it. The spine does not route test fixtures.
