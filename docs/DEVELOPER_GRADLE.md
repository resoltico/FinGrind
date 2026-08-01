---
afad: "5.0.1"
version: "0.62.0"
domain: DEVELOPER_GRADLE
updated: "2026-07-30"
route:
  keywords: [fingrind, gradle, powershell7, windows-wrapper, build-logic, composite-build, version-catalog, contract-lint, jazzer, buildsrc, managed-sqlite, sqlite3mc, toolchain, verification]
  questions: ["how is the fingrind gradle build structured", "why does the Windows Gradle wrapper require PowerShell 7", "why does fingrind use gradle/build-logic instead of buildSrc", "how does the nested jazzer build consume the root project", "where are shared gradle conventions defined", "how does contract linting protect operation metadata", "what should we review in the gradle setup"]
---

# Gradle Setup Reference

**Purpose**: Explain how FinGrind's Gradle system is arranged after the workstation-level Java and wrapper setup from [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md) is already in place.
**Companion references**: [DEVELOPER.md](./DEVELOPER.md),
[DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md), [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)

---

## Canonical Execution

FinGrind's machine-level setup rule is simple:
- use `./gradlew` for every macOS/Linux repo build command and `./gradlew.bat` for every Windows
  repo build command
- treat `gradle` on `PATH` as outside the supported FinGrind workflow
- on Windows, invoke `gradlew.bat` from PowerShell 7 (`pwsh`); Windows PowerShell
  (`powershell.exe`) is not supported
- before a full root verification run, provision the exact metadata-pinned PowerShell `7.6.4`
  runtime for the mandatory Windows-contract preflight as described in
  [DEVELOPER_CI.md](./DEVELOPER_CI.md)
- let the wrapper download the official Gradle distribution pinned by the repository
- bootstrap the pinned repo-owned `uv` launcher on the active shell's Python surface when you run
  root verification, because `check` now includes Ruff over `scripts/**/*.py` and SQLFluff over
  the canonical SQLite schema
- prefer local checkout storage for speed, while allowing mounted checkouts through wrapper-owned
  cache relocation

The wrapper version is currently `9.6.1`, as declared in
[gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties).
Treat wrapper upgrades as supply-chain-sensitive changes and keep them on the current verified
stable `9.6.x` line.

This file therefore documents build architecture and ownership boundaries, not how to install a
global Gradle command on a machine.

Wrapper integrity is part of the standard setup:
- `gradle/wrapper/gradle-wrapper.properties` pins the distribution URL and its
  `distributionSha256Sum`
- CI's wrapper-validation job validates the checked-in wrapper surface and is a required `Gate` dependency
- contributors should treat wrapper-file edits as supply-chain-sensitive changes, not as routine noise

Full verification now depends on the wrapper-managed filesystem layout:
- the POSIX wrapper and the Windows PowerShell wrapper owner inject one per-checkout
  `--project-cache-dir` under their wrapper-owned cache root unless the caller selected one
- both wrappers also inject `-Dfingrind.gradle.build-logic-dir=...` and
  `-Dfingrind.gradle.jacoco-root=...` so included-build output and JaCoCo execution data stay
  under that cache root unless the caller selected those properties
- the POSIX wrapper injects `-Dfingrind.gradle.project-build-root=...` by default so the root
  build, subprojects, and nested Jazzer include-build no longer treat the checkout itself as the
  ordinary build-cache destination; the Windows wrapper does so only for a UNC checkout or an
  explicit `FINGRIND_GRADLE_PROJECT_BUILD_ROOT`, preserving the local-drive Windows build layout
- the bundle and Docker smoke scripts now resolve those conditional build directories through the
  same wrapper helper instead of hardcoding `cli/build/...`
- the default cache root is `~/Library/Caches/FinGrind/gradle-project-cache/<repo-hash>` on
  macOS, `$XDG_CACHE_HOME/fingrind/gradle-project-cache/<repo-hash>` on other Unix-like systems
  when `XDG_CACHE_HOME` is set, then `~/.cache/fingrind/gradle-project-cache/<repo-hash>`, then a
  `TMPDIR` or `/tmp` fallback
- on Windows the wrapper prefers an explicit `FINGRIND_GRADLE_PROJECT_CACHE_ROOT`, then
  `RUNNER_TEMP`, then `TEMP`, then `LOCALAPPDATA`, then a checkout-local
  `.gradle-project-cache` fallback, so GitHub-hosted runners keep wrapper-owned cache state on
  the working drive instead of drifting onto a cross-drive temp root
- mounted external volumes can still host the checkout because wrapper-owned and build-owned
  transient state no longer needs to live there
- every wrapper-launched Gradle invocation holds one operating-system-backed, per-checkout lease
  for the full Gradle child lifetime; a competing wrapper invocation waits rather than sharing
  mutable build output and reports both the wait and subsequent lease acquisition on standard error
- the lease is independent of caller-selected cache and output paths; set
  `FINGRIND_GRADLE_INVOCATION_LEASE_ROOT` only when the lease itself needs an explicit location;
  the Windows last-resort checkout-local `.gradle-invocation-leases/` directory is ignored,
  admitted by repository hygiene as generated state, and removable with `--purge-generated-state`
- the wrapper honors `FINGRIND_GRADLE_PROJECT_CACHE_ROOT`, `FINGRIND_GRADLE_PROJECT_CACHE_DIR`,
  `FINGRIND_GRADLE_BUILD_LOGIC_DIR`, `FINGRIND_GRADLE_JACOCO_ROOT`, and
  `FINGRIND_GRADLE_PROJECT_BUILD_ROOT` for explicit override cases, but the wrapper defaults are
  the canonical contributor path

On Windows, `gradlew.bat` deliberately does no cache, build-root, Java, or Gradle-option policy.
It resolves `pwsh.exe`, anchors the repository, forwards the original Gradle argument vector, and
relays the child exit code. `scripts/gradlew.ps1` rejects PowerShell before version 7, and its
`gradle-wrapper-owner.ps1` builds the Java invocation from the canonical Windows path plan. It
parses `JAVA_OPTS` and `GRADLE_OPTS` with Windows quote/backslash argument rules and launches Java
through `ProcessStartInfo.ArgumentList`, so quotes, empty arguments, Unicode, and trailing
backslashes do not become a joined shell string. It keeps an interactive console inherited, but
copies redirected input to Java and closes that pipe before waiting so Gradle receives EOF instead
of deadlocking behind PowerShell. The wrapper rejects unsupported PowerShell majors; the mandatory
local Windows-contract preflight is deliberately stricter and proves its owner and
adapter structure; GitHub's native Windows job remains the authority for `cmd.exe`, PowerShell
process startup, MSVC, and NTFS behavior.

---

## System Map

FinGrind has three distinct Gradle layers:

1. the root product build
2. the shared included build logic
3. the nested Jazzer build

```text
settings.gradle.kts
build.gradle.kts
gradle/
├── libs.versions.toml
└── build-logic/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    └── src/main/kotlin/dev/erst/fingrind/buildlogic/
        ├── FinGrindJavaConventionsPlugin.kt
        ├── FinGrindJavaRuntimeConventions.kt
        ├── FinGrindJavaQualityConventions.kt
        ├── FinGrindJavaCoverageConventions.kt
        ├── FinGrindRootFormattingConventions.kt
        ├── FinGrindRootPythonSqlConventions.kt
        ├── FinGrindRootCoverageConventions.kt
        ├── FinGrindRootJazzerConventions.kt
        ├── FinGrindRootConventionsPlugin.kt
        ├── FinGrindJazzerConventionsPlugin.kt
        ├── ManagedSqliteProvisioningLogic.kt
        ├── ScheduledPulseTestListener.kt
        ├── GradleTestPulseListener.kt
        ├── JazzerDeterministicTestPulseListener.kt
        └── ...
core/
contract/
executor/
sqlite/
report-pdf/
cli/
jazzer/
├── settings.gradle.kts
└── build.gradle.kts
```

Each layer owns a different concern:

- root product build: builds and verifies `core`, `contract`, `executor`, `sqlite`, `report-pdf`,
  `cli`, and the independent `architecture` verifier
- shared included build logic: houses reusable Gradle plugins, managed-SQLite tasks, and shared
  pulse infrastructure
- nested Jazzer build: runs deterministic Jazzer tests, regression replay, and local fuzzing flows

The root build intentionally does not include `jazzer/` as a normal subproject. Jazzer remains a
separate nested build because its runtime model, local state, and operator flows are intentionally
different from the main product modules.

---

## Why It Is Set Up This Way

### Shared included build logic instead of `buildSrc`

FinGrind used to carry shared Gradle logic in `buildSrc` and also duplicated some build-only types
inline inside `build.gradle.kts` files. That arrangement had three problems:

- root and Jazzer build logic could drift independently
- deleted helper classes could survive as stale compiled artifacts in local Gradle state
- large Kotlin build scripts mixed configuration with typed implementation details

The current setup replaces `buildSrc` with one explicit included build under `gradle/build-logic`.
That gives the repository one home for shared plugins, one review surface for Gradle behavior, and
one place to fix infrastructure concerns such as test pulses or managed-SQLite provisioning.

The included build now relies on Gradle's normal up-to-date and incremental Kotlin compilation
behavior. It no longer force-disables Kotlin incremental compilation or wipes its own compile
output directories before every build. On the Java side, both the shared product-module
conventions and the nested Jazzer build prune the main source-set destination directory before
each real `compileJava` run so removed helper types cannot linger as orphaned `.class` files in
the wrapper-owned project cache and poison coverage verification. The nested Jazzer build also
prunes each processed-resource destination directory before real resource syncs so renamed or
removed committed seeds cannot linger in `jazzer-build/resources/` and skew replay or packaged
corpus behavior away from `src/fuzz/resources`.
Its own Kotlin compiler is pinned explicitly through
`gradle/fingrind-build.properties` at `2.4.10` so the shared build logic can compile
against Gradle's Kotlin DSL APIs while still emitting JVM 26 bytecode.

The consumer scripts are intentionally thin now:
- root `build.gradle.kts` is a single root-conventions plugin application
- Java module policy is composed from `FinGrindJavaConventionsPlugin`,
  `FinGrindJavaRuntimeConventions`, `FinGrindJavaQualityConventions`, and
  `FinGrindJavaCoverageConventions`
- repository-wide formatting, Python/SQL verification, aggregated coverage, and root-owned Jazzer
  verification are composed by `FinGrindRootConventionsPlugin` from
  `FinGrindRootFormattingConventions`, `FinGrindRootPythonSqlConventions`,
  `FinGrindRootCoverageConventions`, and `FinGrindRootJazzerConventions`

Root verification now has one explicit Python-tooling contract:
- the pinned exact CI interpreter version is `fingrindPythonVersion` from
  [../gradle/fingrind-build.properties](../gradle/fingrind-build.properties)
- the pinned repo-owned `uv` launcher version is `fingrindUvVersion` from
  [../gradle/fingrind-build.properties](../gradle/fingrind-build.properties)
- the pinned lint-tool manifest is
  [../requirements-python-tools.txt](../requirements-python-tools.txt)
- Gradle executes those helper tools through `python -m uv tool run --with-requirements ...`
  instead of importing whatever ambient packages happen to be installed
- the repo-owned Ruff configuration is [../ruff.toml](../ruff.toml)
- the repo-owned SQLFluff configuration is [../gradle/sqlfluff/sqlfluff.cfg](../gradle/sqlfluff/sqlfluff.cfg)
- contributors can override the executable Gradle uses with
  `-PfingrindPythonExecutable=/absolute/path/to/python3.12`; the selected interpreter must match
  the pinned major/minor version exactly

Release-smoke semantic PDF verification is deliberately outside that lint/format manifest. The
same pinned `uv` launcher resolves its sole extractor from
[../requirements-release-smoke-workflow.txt](../requirements-release-smoke-workflow.txt), so the
bundle, Windows, and compatibility-floor workflows never depend on an ambient `pdftotext` or other
host PDF utility. Keeping the extractor isolated prevents lint-only dependencies from becoming
release-smoke runtime requirements and keeps every release surface on one parser contract.

### Composite build for Jazzer

`jazzer/settings.gradle.kts` uses `includeBuild("..")` so the nested build can resolve the live
local product modules without publishing snapshots. This keeps Jazzer iteration fast and ensures
fuzzing runs against the exact working tree under review.

The nested Jazzer build now also applies the same `dev.erst.fingrind.java-conventions` plugin that
the main Java modules use, so its own replay engine, CLI utilities, and tests no longer bypass
Spotless, Error Prone, NullAway, PMD, JaCoCo, or the shared source/Jackson policy tasks.
The checked-in PMD XML files under `gradle/pmd/` and `jazzer/gradle/pmd/` are derived from the
canonical `FinGrindPmdRulesets.kt` owner in `gradle/build-logic`, and that canonical policy
excludes PMD `NcssCount` explicitly because FinGrind's structural-governance verifiers own file and
method size budgets.

### One dependency authority

The root version catalog in `gradle/libs.versions.toml` is the shared dependency authority. The
nested Jazzer build imports that catalog instead of repeating overlapping coordinates locally. That
avoids silent version skew between the main product modules and Jazzer support code.

The shared repository owner now also lives in `gradle/build-logic`: FinGrind uses Maven Central as
the default repository. JaCoCo now follows that same one-owner rule: the shared version catalog
pins the stable `0.8.15` line, the root and Java Gradle conventions read that exact version
directly, and `./scripts/verify-jacoco-artifacts.sh` proves the four published GA jars exist in
Maven Central before any Gradle verification stage runs.

### One managed-SQLite contract

Both the root build and the nested Jazzer build compile the managed SQLite 3.53.4 / SQLite3
Multiple Ciphers 2.4.0 runtime from the same vendored official amalgamation, through the same
typed Gradle tasks. That keeps tests, CLI runs, and fuzzing on one native runtime contract instead
of letting Gradle surfaces drift onto whatever system `libsqlite3` happened to be present.

That contract now has a few explicit rules:
- the vendored source of truth is `third_party/sqlite/sqlite3mc-amalgamation-2.4.0-sqlite-3530400/`
- `verifyManagedSqliteSource` hashes `sqlite3mc_amalgamation.c`, not the plain `sqlite3.c`
- managed builds compile with `SQLITE_THREADSAFE=1`, `SQLITE_OMIT_LOAD_EXTENSION=1`,
  `SQLITE_TEMP_STORE=3`, `SQLITE_SECURE_DELETE=1`, and `SQLITE3MC_SECURE_MEMORY=1`
- managed/runtime compatibility also forbids the SQLite compile option `USE_URI`
- `:cli:bundleCliArchive` is the public-artifact packaging entrypoint; it assembles the app JAR,
  private Java runtime image, managed native library, launcher, and checksum, then writes the
  exact archive/checksum paths to `generated/bundle/bundle-archive-manifest.json` under the active
  CLI build directory
- `:cli:stageDockerBuildContext` is the Docker assembly entrypoint; it stages one canonical Docker
  build context under the active CLI build root
- that Docker staging path owns a Linux-target managed SQLite artifact distinct from the
  host-native bundle artifact when the current workstation is not Linux; the same Gradle-managed
  native build contract decides both paths, and non-Linux hosts materialize the Docker target
  through a pinned Buildx builder image instead of relabeling host-native libraries
- that staged context contains `Dockerfile`, `fingrind.jar`, `runtime-modules.txt`,
  `docker-entrypoint.sh`, `libsqlite3.so.0`, `libsqlite3.so.0.sha256`,
  `toolchain-fingerprint.json`, `build-contract.json`, `docker-build-context-manifest.json`, and
  a `source-root/` snapshot of every checked source/build input the container assembly depends on
- that staged manifest also fingerprints the current CLI, contract, core, executor, report-PDF,
  SQLite, Gradle build logic, Dockerfile, helper scripts, vendored SQLite source, and legal files;
  Docker build verifies that fingerprint against the current repository copy so stale staged
  contexts fail loudly instead of silently shipping old launcher, contract, or jar bytes
- repository-root `docker build .` is intentionally unsupported; Docker assembly consumes the
  staged context directory, not the whole checkout
- `:cli:shadowJar` remains an internal assembly input for `:cli:stageDockerBuildContext` and
  advanced contributor debugging; it does not build a native library on its own
- `prepareManagedSqlite` is the separate Gradle step that produces the managed host library under
  `build/managed-sqlite/`
- `prepareDockerManagedSqlite` is the Docker-target companion step that appears only when the
  current workstation cannot natively produce the Linux container artifact; local Docker acceptance
  and Docker-context staging call it through `:cli:stageDockerBuildContext` rather than asking
  contributors to invoke it directly. That task now compiles inside one repo-owned anonymous
  `docker run --platform …` builder container instead of nesting a second Buildx image assembly,
  so public Linux artifact preparation is observable and does not inherit personal Docker Desktop
  credential-helper state
- local developer direct-Java verification uses `./scripts/direct-java-cli.sh` or
  `.\scripts\direct-java-cli.ps1` and therefore runs `:cli:writeSourceCheckoutRuntimeManifest`
  plus `prepareManagedSqlite` before launching the cached raw JAR
- the repo-owned source-checkout and direct-Java wrappers now delegate checkout freshness to the
  Gradle-owned runtime-preparation tasks instead of replaying source hashing in shell. Each
  launch refreshes `:cli:writeSourceCheckoutRuntimeManifest` plus `prepareManagedSqlite`, then
  executes through the generated Java 26 runtime manifest

### Committed Jazzer topology

The Jazzer harness and run-target inventory lives in
`jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-harnesses.json` and
`jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-run-targets.json`. Shared
Gradle build logic consumes those catalogs for task registration, and Jazzer runtime support
classes consume the same catalogs for stable key lookup and topology assertions. That removes the
old duplicated manual registry split between build logic and runtime code.

That same committed topology now feeds the nested Gradle `jazzerActiveTargets` and
`jazzerReplayableTargets` query tasks, the repo-owned shell topology reader, and the Java operator
entrypoints. The two JSON catalogs are the owner; Gradle, shell wrappers, and runtime support are
projections over that one topology rather than separate registries.

### Thin consumer build scripts

Large `.gradle.kts` files are hard to test, hard to refactor, and easy to let drift into mixed
configuration-plus-implementation blobs. FinGrind therefore keeps reusable typed logic in
`gradle/build-logic` and keeps consumer scripts thin. `jazzer/build.gradle.kts` is intentionally a
single plugin application for exactly that reason.

That same rule now applies inside the included build itself. The root and Java convention plugins
remain the orchestration seams, but repository formatting, Python/SQL verification, aggregated
coverage, Java runtime wiring, Java quality gates, and Java coverage wiring each live in focused
build-logic owners instead of re-growing broad plugin god files.

### Coverage gate protocol

FinGrind's JaCoCo wiring is intentionally stricter than JaCoCo's defaults because the defaults can
silently under-enforce the documented coverage contract.

Rules:
- never rely on JaCoCo's built-in verification defaults or unnamed `limit {}` semantics for
  FinGrind's quality gate meaning
- treat the generated `jacocoTestReport.xml` counters as the authoritative module coverage truth
  and fail the gate when that report shows any missed `LINE` or `BRANCH` coverage
- apply that production coverage rule only to modules with production Java sources; a deliberately
  test-only verifier still runs its tests and static-quality tasks, but has no artificial production
  line or branch denominator
- reject a report with a zero `LINE` denominator: an empty report cannot evidence production-code
  coverage, while a module with no conditional bytecode may legitimately have a zero `BRANCH`
  denominator
- each `Test` task must delete its own destination `.exec` file before execution and use
  `append=true` only within that one task run, so cross-run coverage drift cannot survive into the
  next verification pass
- admit report data only when every current module `Test` task actually executed in the same Gradle
  invocation, with no task exclusion, file include/exclude, test filter, or command-line `--tests`
  selection; the canonical quality gate therefore runs its root `check coverage` invocation with
  `--rerun-tasks`
- wire both `jacocoTestReport` and `jacocoTestCoverageVerification` to the same execution-data
  scope so reporting and verification cannot disagree
- collect every local `build/jacoco/*.exec` file for a module instead of hardcoding only
  `build/jacoco/test.exec`
- make coverage verification depend on `tasks.withType<Test>()` so any added `Test` task must run
  before the gate evaluates
- at the root aggregated-report layer, collect every subproject `build/jacoco/*.exec` file instead
  of assuming one `test.exec` per module, and reject an aggregate that omits a direct production
  Java project rather than allowing that project to remain outside the module convention

Why this rule exists:
- if a project later adds `integrationTest`, `parityTest`, or any other extra `Test` task, a
  hardcoded `test.exec` assumption can silently exclude real execution data from the gate
- if a project reuses stale `.exec` files across separate test runs, the report can inherit dead
  execution data and stop describing the current tree honestly
- if a targeted test selector or an omitted `Test` task can feed a report, a small passing subset
  can falsely represent full production coverage
- if a direct production Java project is absent from the aggregate, root coverage can be green
  while a whole product surface is unmeasured
- when previously unseen uncovered code appears after fixing JaCoCo wiring, treat that as the gate
  becoming truthful rather than as the code suddenly regressing
- if the toolchain's built-in verification task disagrees with the generated report counters, trust
  the generated report and fix the verification wiring rather than weakening the coverage bar

Repository-specific note:
- FinGrind's product modules currently use only the default Gradle `test` task
- even so, FinGrind now collects all local `build/jacoco/*.exec` files in both the per-module
  and aggregated coverage surfaces so a future second `Test` task cannot bypass the quality gate
- a direct production Java project must apply `dev.erst.fingrind.java-conventions` to participate
  in the root aggregate; the aggregate admission task rejects an accidental omission immediately
- the nested Jazzer build remains intentionally separate from root product-module coverage;
  `jazzer/bin/check` is the authoritative deterministic Jazzer coverage gate and runs a clean
  invocation before a separate nested verification invocation under one held repository lock

### Contract lint protocol

FinGrind keeps public operation metadata in the contract protocol catalog and treats drift as a build
failure.

Rules:
- production Java outside contract protocol must not embed hyphenated operation ids inside string
  literals
- documentation command examples that invoke `fingrind` must reference registered operation ids
- backticked hyphen identifiers in docs must either be registered operations or explicitly known
  non-operation identifiers such as rejection codes, platform classifiers, or Jazzer harness keys
- catalog usage and quick-start examples must reference only registered operations
- bundle machine metadata must point at canonical protocol operations instead of reauthoring static
  command-group arrays

Why this rule exists:
- agent-facing help, docs, parser aliases, capabilities summaries, plan templates, and error hints
  must converge
  on one command vocabulary
- adding or renaming a command should fail fast unless the contract protocol registry, docs, and
  renderers stay in sync
- embedding `open-book`, `list-postings`, or similar ids inside larger user-facing strings is the
  same drift class as reauthoring the raw literal directly; both create a second owner for command
  vocabulary
- bundle bootstrap metadata must stay truthful without maintaining a shadow registry next to the
  real protocol catalog

### Source and dependency policy

FinGrind now treats import style and Jackson dependency ownership as first-class build invariants,
not review-time preferences.

Rules:
- Java source files under any `src/*/java` tree must not use wildcard imports
- production Java source files under any `src/main/java` tree must not catch `Throwable`
- production Java source files under any `src/main/java` tree must not use `@SuppressWarnings`
- direct Jackson dependencies may only enter through tools.jackson.core:jackson-databind
- direct `com.fasterxml.jackson.core:*` declarations are forbidden even in tests or the nested
  Jazzer build
- do not add a separate repo-owned jackson-annotations version pin; FinGrind inherits the
  annotation artifact selected by the approved Jackson databind entrypoint
- source imports from `com.fasterxml.jackson.annotation` are still correct here because the
  approved Jackson 3 databind entrypoint intentionally keeps using that upstream annotation
  namespace

Why this rule exists:
- wildcard imports hide real source dependencies and make architectural review harder
- `catch (Throwable)` blurs the line between recoverable bridge failures and JVM-fatal `Error`
  paths, which misclassifies failures and breaks the runtime hinting contract
- production `@SuppressWarnings` entries are almost always structural debt markers in this repo and
  should be refactored away rather than normalized into the main source tree
- the repeated Jackson 2.x vs 3.x review churn came from leaving the repo without an explicit
  ownership rule, even though the runtime behavior was already exercised by tests
- one direct Jackson entrypoint means upgrades happen in one place, while source and replay tests
  keep null omission and polymorphic replay behavior honest

Repository-specific note:
- `verifyJavaSourcePolicies` now fails `check` when wildcard imports appear in Java source sets
- `verifyJavaSourcePolicies` also fails `check` when production Java source sets introduce
  `catch (Throwable)` or production `@SuppressWarnings`
- `verifyJacksonDependencyPolicy` now fails `check` for any direct Jackson dependency declaration
  outside tools.jackson.core:jackson-databind
- these checks run in both the root product build and the nested Jazzer build

---

## Ownership Boundaries

Use this routing table before changing the build:

| If you are changing... | Change here first |
|:-----------------------|:------------------|
| root project membership, plugin resolution | `settings.gradle.kts` |
| root build wiring only | `build.gradle.kts` |
| repository-wide formatting, Python/SQL checks, aggregated coverage, and root Jazzer gate wiring | `gradle/build-logic/.../FinGrindRootConventionsPlugin.kt` as the composition entrypoint, plus the focused `FinGrindRoot*Conventions.kt` owners |
| shared Java subproject conventions | `gradle/build-logic/.../FinGrindJavaConventionsPlugin.kt` as the composition entrypoint, plus the focused `FinGrindJava*Conventions.kt` owners |
| managed-SQLite root publication and consumer wiring | `gradle/build-logic/.../FinGrindManagedSqliteConsumerPlugin.kt`, `ManagedSqliteProvisioningRegistry.kt`, and `ManagedSqliteProvisioningLogic.kt` |
| managed-SQLite task types and shared helpers | `gradle/build-logic/.../ManagedSqliteProvisioningLogic.kt` and task classes nearby |
| shared Jazzer build behavior and Jazzer task registration | `gradle/build-logic/.../FinGrindJazzerConventionsPlugin.kt` |
| root-owned deterministic Jazzer verification | `gradle/build-logic/.../FinGrindRootJazzerConventions.kt` |
| shared pulse scheduling | `gradle/build-logic/.../ScheduledPulseTestListener.kt` and concrete listeners |
| dependency versions shared across product and Jazzer | `gradle/libs.versions.toml` |
| nested Jazzer plugin wiring or imported catalogs | `jazzer/settings.gradle.kts` |
| Jazzer harness and run-target topology | `jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-harnesses.json`, `jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-run-targets.json` |

Rules:

- do not add reusable typed logic back into module-local `.gradle.kts` files
- do not reintroduce `buildSrc`
- do not hardcode overlapping dependency versions inside `jazzer/`
- do not make the root build depend on active fuzzing tasks
- do not assume Bash 4+ array semantics in `jazzer/bin/*`; the supported macOS operator surface is
  stock `/bin/bash` 3.2 under `set -u`
- do not run root and nested Jazzer Gradle builds in parallel against the same workspace

---

## Stable Invariants

These are the Gradle-level invariants worth preserving:

- `core`, `contract`, `executor`, `sqlite`, `report-pdf`, `cli`, and `architecture` remain ordinary
  root subprojects
- `jazzer/` remains a nested build, not a root subproject
- `gradle/build-logic` remains the only home for shared typed Gradle logic
- the repository contains no active `buildSrc` tree
- the nested Jazzer build imports `../gradle/libs.versions.toml`
- root and nested Gradle surfaces use the same vendored SQLite3MC source and managed runtime
  contract
- shared pulse scheduling lives in one base implementation, with build-specific listeners layered on
  top
- the Jazzer harness and run-target catalogs remain the single source of truth for harness keys,
  task names, and working-directory ownership
- root `./gradlew check` stays focused on the product modules
- active Jazzer fuzzing remains a wrapper-owned local operator flow through `jazzer/bin/*`
- root `./check.sh` remains the supported whole-repo gate that sequences root verification, Jazzer
  verification, packaging, and Docker smoke checks
- root `./check.sh`, `scripts/docker-smoke.sh`, `scripts/validate-devcontainer.sh`, and
  `jazzer/bin/*` continue to share one repo-wide verification lock plus repo-keyed cache-root
  `GRADLE_USER_HOME` isolation
- root `./check.sh` and `./scripts/run-quality-gates.sh` resolve the repo-owned Python helper-tool
  runtime automatically; when the shell does not expose exact Python `3.12`, they fall back to a
  `uv`-managed exact Python `3.12` interpreter and pass it into Gradle as
  `fingrindPythonExecutable`

If a proposed change breaks one of those invariants, document the reason in code comments and in
the changelog instead of letting the system drift silently.

---

## Review Checklist

Review this setup periodically, especially after Gradle, Kotlin, SQLite, or Jazzer upgrades:

- Does `gradle/build-logic` still compile and emit JVM 26 bytecode after the current Kotlin plugin pin?
- Is the Gradle wrapper still on the current verified stable `9.5.x` line?
- Is the build-logic Kotlin pin `2.4.10`, and does the included build still verify cleanly on the live 2.4.x line?
- Has anyone reintroduced manual output wiping or disabled incremental compilation in
  `gradle/build-logic`, beyond the deliberate Jazzer source-set pruning that prevents orphaned
  cached classfiles?
- Is any dependency version duplicated outside `gradle/libs.versions.toml`?
- Has any typed logic crept back into a leaf `.gradle.kts` script?
- Are root and nested verification scopes still cleanly separated?
- Are long-running test pulses still emitted from shared infrastructure rather than copy-pasted
  listeners?
- Are root and nested builds still using the same managed SQLite 3.53.4 / SQLite3 Multiple
  Ciphers 2.4.0 runtime contract?
- Is source verification still pinned to the official SQLite3 Multiple Ciphers release input rather
  than an ad-hoc host library or repackaged archive?
- Do the `jazzer/bin/*` wrappers still work on stock macOS `/bin/bash` 3.2 when no optional
  Gradle arguments are passed?
- Does the nested Jazzer build still need to stay independent from the root project graph?
- Are configuration-cache or composite-build constraints forcing awkward workarounds that deserve a
  redesign instead?

This file exists so those questions can be reviewed against the current system rather than against
half-remembered history.

---

## Change Workflow

When changing the Gradle system:

1. update the owning build file or plugin, not just the consuming script
2. update companion docs if the contributor workflow or architecture changed
3. run the smallest verification that proves the change, then the supported whole-repo gate when
   the change is structural

For structural Gradle changes, the normal bar is:

```bash
./gradlew check
jazzer/bin/check --console=plain
./check.sh
```

If Jazzer topology or `jazzer/bin/*` wrapper shell logic changed, also run at least one live
`jazzer/bin/fuzz-*` command and inspect its retained evidence through `jazzer/bin/list-findings`
so the documented operator path is exercised in the same shape contributors will actually use.
