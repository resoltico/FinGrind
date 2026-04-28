---
afad: "3.5"
version: "0.28.0"
domain: DEVELOPER_GRADLE
updated: "2026-04-28"
route:
  keywords: [fingrind, gradle, build-logic, composite-build, version-catalog, contract-lint, jazzer, buildsrc, managed-sqlite, sqlite3mc, toolchain, verification]
  questions: ["how is the fingrind gradle build structured", "why does fingrind use gradle/build-logic instead of buildSrc", "how does the nested jazzer build consume the root project", "where are shared gradle conventions defined", "how does contract linting protect operation metadata", "what should we review in the gradle setup"]
---

# Gradle Setup Reference

**Purpose**: Explain how FinGrind's Gradle system is arranged after the workstation-level Java and wrapper setup from [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md) is already in place.
**Companion references**: [DEVELOPER.md](./DEVELOPER.md),
[DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md), [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)

---

## Canonical Execution

FinGrind's machine-level setup rule is simple:
- use `./gradlew` for every repo build command
- treat `gradle` on `PATH` as outside the supported FinGrind workflow
- let the wrapper download the official Gradle distribution pinned by the repository
- prefer local checkout storage for speed, while allowing mounted checkouts through wrapper-owned
  cache relocation

The wrapper version is currently `9.5.0-rc-4`, as declared in
[gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties).
That pin is intentionally temporary and prerelease-sensitive: move to the matching stable `9.5.x`
line as soon as it exists and the wrapper/custom-launcher surface verifies cleanly.

This file therefore documents build architecture and ownership boundaries, not how to install a
global Gradle command on a machine.

Wrapper integrity is part of the standard setup:
- `gradle/wrapper/gradle-wrapper.properties` pins the distribution URL and its
  `distributionSha256Sum`
- `.github/workflows/gradle-wrapper-validation.yml` validates wrapper changes in GitHub
- contributors should treat wrapper-file edits as supply-chain-sensitive changes, not as routine noise

Full verification now depends on the wrapper-managed filesystem layout:
- `gradlew` injects one per-checkout `--project-cache-dir` outside the repository
- `gradlew` also injects `-Dfingrind.gradle.build-logic-dir=...` and
  `-Dfingrind.gradle.jacoco-root=...` so included-build output and JaCoCo execution data stay off
  fragile checkout filesystems
- when the checkout itself lives on a fragile mounted filesystem such as `smbfs`, `gradlew` also
  injects `-Dfingrind.gradle.project-build-root=...` so the root build, subprojects, and nested
  Jazzer include-build no longer try to delete or rewrite in-repo `build/` trees there
- the bundle and Docker smoke scripts now resolve those conditional build directories through the
  same wrapper helper instead of hardcoding `cli/build/...`
- the default cache root is `~/Library/Caches/FinGrind/gradle-project-cache/<repo-hash>` on
  macOS, `$XDG_CACHE_HOME/fingrind/gradle-project-cache/<repo-hash>` on other Unix-like systems
  when `XDG_CACHE_HOME` is set, then `~/.cache/fingrind/gradle-project-cache/<repo-hash>`, then a
  `TMPDIR` or `/tmp` fallback
- on Windows the wrapper prefers an explicit `FINGRIND_GRADLE_PROJECT_CACHE_ROOT`, then
  `RUNNER_TEMP`, then `TEMP`, then `LOCALAPPDATA`, so GitHub-hosted runners keep wrapper-owned
  cache state on the working drive instead of drifting onto a cross-drive temp root
- mounted external volumes can still host the checkout because the lock-sensitive Gradle state no
  longer needs to live there
- the wrapper honors `FINGRIND_GRADLE_PROJECT_CACHE_ROOT`, `FINGRIND_GRADLE_PROJECT_CACHE_DIR`,
  `FINGRIND_GRADLE_BUILD_LOGIC_DIR`, `FINGRIND_GRADLE_JACOCO_ROOT`, and
  `FINGRIND_GRADLE_PROJECT_BUILD_ROOT` for explicit override cases, but the wrapper defaults are
  the canonical contributor path

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

- root product build: builds and verifies `core`, `contract`, `executor`, `sqlite`,
  `report-pdf`, and `cli`
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

The included build now relies on Gradle's normal up-to-date and incremental compilation behavior.
It no longer force-disables Kotlin incremental compilation or wipes compile output directories
before every build.
Its own Kotlin compiler is now pinned explicitly in
`gradle/build-logic/build.gradle.kts` at `2.4.0-Beta2` so the shared build logic can compile
against Gradle's Kotlin DSL APIs while still emitting JVM 26 bytecode.
That Kotlin pin is also intentionally temporary and prerelease-sensitive: move to the matching
stable `2.4.x` line as soon as it is available and the included build still verifies cleanly on
the live plugin/classpath surface.

The consumer scripts are intentionally thin now:
- root `build.gradle.kts` is a single root-conventions plugin application
- Java module policy lives in `FinGrindJavaConventionsPlugin`
- repository-wide formatting, aggregated coverage, and managed-SQLite root wiring live in
  `FinGrindRootConventionsPlugin`

### Composite build for Jazzer

`jazzer/settings.gradle.kts` uses `includeBuild("..")` so the nested build can resolve the live
local product modules without publishing snapshots. This keeps Jazzer iteration fast and ensures
fuzzing runs against the exact working tree under review.

The nested Jazzer build now also applies the same `dev.erst.fingrind.java-conventions` plugin that
the main Java modules use, so its own replay engine, CLI utilities, and tests no longer bypass
Spotless, Error Prone, NullAway, PMD, JaCoCo, or the shared source/Jackson policy tasks.

### One dependency authority

The root version catalog in `gradle/libs.versions.toml` is the shared dependency authority. The
nested Jazzer build imports that catalog instead of repeating overlapping coordinates locally. That
avoids silent version skew between the main product modules and Jazzer support code.

### One managed-SQLite contract

Both the root build and the nested Jazzer build compile the managed SQLite 3.53.0 / SQLite3
Multiple Ciphers 2.3.3 runtime from the same vendored official amalgamation, through the same
typed Gradle tasks. That keeps tests, CLI runs, and fuzzing on one native runtime contract instead
of letting Gradle surfaces drift onto whatever system `libsqlite3` happened to be present.

That contract now has a few explicit rules:
- the vendored source of truth is `third_party/sqlite/sqlite3mc-amalgamation-2.3.3-sqlite-3530000/`
- `verifyManagedSqliteSource` hashes `sqlite3mc_amalgamation.c`, not the plain `sqlite3.c`
- managed builds compile with `SQLITE_THREADSAFE=1`, `SQLITE_OMIT_LOAD_EXTENSION=1`,
  `SQLITE_TEMP_STORE=3`, and `SQLITE_SECURE_DELETE=1`
- `:cli:bundleCliArchive` is the public-artifact packaging entrypoint; it assembles the app JAR,
  private Java runtime image, managed native library, launcher, and checksum
- `:cli:shadowJar` remains an internal assembly input for Docker and advanced contributor
  debugging; it does not build a native library, but it does stage the shared Docker runtime
  module list under `cli/build/docker/runtime-modules.txt`
- `prepareManagedSqlite` is the separate Gradle step that produces the managed host library under
  `build/managed-sqlite/`
- local developer-only `java -jar` verification that wants the managed runtime must therefore run
  both `:cli:shadowJar` and `prepareManagedSqlite`

### Committed Jazzer topology

The Jazzer harness and run-target inventory lives in
`jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-topology.json`. Shared Gradle
build logic consumes that file for task registration, and Jazzer runtime support classes consume
the same file for stable key lookup and topology assertions. That removes the old duplicated manual
registry split between build logic and runtime code.

### Thin consumer build scripts

Large `.gradle.kts` files are hard to test, hard to refactor, and easy to let drift into mixed
configuration-plus-implementation blobs. FinGrind therefore keeps reusable typed logic in
`gradle/build-logic` and keeps consumer scripts thin. `jazzer/build.gradle.kts` is intentionally a
single plugin application for exactly that reason.

### Coverage gate protocol

FinGrind's JaCoCo wiring is intentionally stricter than JaCoCo's defaults because the defaults can
silently under-enforce the documented coverage contract.

Rules:
- never rely on an unnamed JaCoCo `limit {}` block for coverage meaning
- always set `counter = "LINE"` and `counter = "BRANCH"` explicitly for verification rules
- always set `value = "COVEREDRATIO"` explicitly for those rules
- treat omitted `counter` as invalid build logic because JaCoCo defaults that case to
  instruction coverage, and 100% instruction coverage does not prove 100% branch coverage
- wire both `jacocoTestReport` and `jacocoTestCoverageVerification` to the same execution-data
  scope so reporting and verification cannot disagree
- collect every local `build/jacoco/*.exec` file for a module instead of hardcoding only
  `build/jacoco/test.exec`
- make coverage verification depend on `tasks.withType<Test>()` so any added `Test` task must run
  before the gate evaluates
- at the root aggregated-report layer, collect every subproject `build/jacoco/*.exec` file instead
  of assuming one `test.exec` per module

Why this rule exists:
- if a project later adds `integrationTest`, `parityTest`, or any other extra `Test` task, a
  hardcoded `test.exec` assumption can silently exclude real execution data from the gate
- if a project omits `limit.counter`, JaCoCo can appear green while whole conditional branches are
  still untested
- when previously unseen uncovered code appears after fixing JaCoCo wiring, treat that as the gate
  becoming truthful rather than as the code suddenly regressing

Repository-specific note:
- FinGrind's product modules currently use only the default Gradle `test` task
- even so, FinGrind now collects all local `build/jacoco/*.exec` files in both the per-module
  and aggregated coverage surfaces so a future second `Test` task cannot bypass the quality gate
- the nested Jazzer build remains intentionally separate from root product-module coverage; its own
  `./gradlew -p jazzer check` is the authoritative Jazzer coverage gate

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
| repository-wide quality gates, root Spotless, aggregated coverage | `gradle/build-logic/.../FinGrindRootConventionsPlugin.kt` |
| shared Java subproject conventions | `gradle/build-logic/.../FinGrindJavaConventionsPlugin.kt` |
| managed-SQLite Gradle provisioning for root modules | `gradle/build-logic/.../FinGrindRootConventionsPlugin.kt` |
| managed-SQLite task types and shared helpers | `gradle/build-logic/.../ManagedSqliteProvisioningLogic.kt` and task classes nearby |
| shared Jazzer build behavior, Jazzer task registration, cleanup tasks | `gradle/build-logic/.../FinGrindJazzerConventionsPlugin.kt` |
| shared pulse scheduling | `gradle/build-logic/.../ScheduledPulseTestListener.kt` and concrete listeners |
| dependency versions shared across product and Jazzer | `gradle/libs.versions.toml` |
| nested Jazzer plugin wiring or imported catalogs | `jazzer/settings.gradle.kts` |
| Jazzer harness and run-target topology | `jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-topology.json` |

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

- `core`, `contract`, `executor`, `sqlite`, `report-pdf`, and `cli` remain ordinary root
  subprojects
- `jazzer/` remains a nested build, not a root subproject
- `gradle/build-logic` remains the only home for shared typed Gradle logic
- the repository contains no active `buildSrc` tree
- the nested Jazzer build imports `../gradle/libs.versions.toml`
- root and nested Gradle surfaces use the same vendored SQLite3MC source and managed runtime
  contract
- shared pulse scheduling lives in one base implementation, with build-specific listeners layered on
  top
- the Jazzer topology file remains the single source of truth for harness keys, task names, and
  working-directory ownership
- root `./gradlew check` stays focused on the product modules
- active Jazzer fuzzing remains a wrapper-owned local operator flow through `jazzer/bin/*`
- root `./check.sh` remains the supported whole-repo gate that sequences root verification, Jazzer
  verification, packaging, and Docker smoke checks

If a proposed change breaks one of those invariants, document the reason in code comments and in
the changelog instead of letting the system drift silently.

---

## Review Checklist

Review this setup periodically, especially after Gradle, Kotlin, SQLite, or Jazzer upgrades:

- Does `gradle/build-logic` still compile and emit JVM 26 bytecode after the current Kotlin plugin pin?
- Is the Gradle wrapper still `9.5.0-rc-4`, and can it move to the matching stable 9.5.x line yet?
- Is the build-logic Kotlin pin still `2.4.0-Beta2`, and can it move to the matching stable 2.4.x line yet?
- Has anyone reintroduced manual output wiping or disabled incremental compilation in
  `gradle/build-logic`?
- Is any dependency version duplicated outside `gradle/libs.versions.toml`?
- Has any typed logic crept back into a leaf `.gradle.kts` script?
- Are root and nested verification scopes still cleanly separated?
- Are long-running test pulses still emitted from shared infrastructure rather than copy-pasted
  listeners?
- Are root and nested builds still using the same managed SQLite 3.53.0 / SQLite3 Multiple
  Ciphers 2.3.3 runtime contract?
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
./gradlew -p jazzer check
./check.sh
```

If Jazzer topology or `jazzer/bin/*` wrapper shell logic changed, also run at least one live
`jazzer/bin/fuzz-*` command plus the zero-argument cleanup scripts so the documented operator path
is exercised in the same shape contributors will actually use.
