# Changelog

Notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0] - 2026-04-10

### Changed
- Hard-broke the posting contract to a reversal-only linkage model by replacing `correction` with
  `reversal`, removing correction kinds, and making reversal semantics explicit across the CLI,
  runtime store, SQLite schema, and reference documentation.
- Rebuilt the committed Jazzer corpus, replay details, and regression metadata around reversal
  terminology, added one explicit legacy-correction rejection seed plus one Unicode SQLite
  round-trip seed, and kept GitHub-side Jazzer verification limited to deterministic support and
  regression replay without active fuzzing.
- Moved remaining repository-wide Gradle policy out of the root `subprojects {}` block and into
  shared convention plugins, leaving the root build script as thin wiring over one `build-logic`
  control plane.
- Switched active Jazzer harness launching onto Jazzer's official JUnit runner and made the local
  harness contract explicit: each active harness class now owns exactly one `@FuzzTest` method.

### Fixed
- Removed remaining native SQLite adapter design debt around hot-path singleton loading,
  statement/mapper contract clarity, and reversal persistence mapping so the FFM-based store now
  matches the stricter reversal-only architecture cleanly.
- Removed redundant UTF-8 re-encoding from the native SQLite text-bind hot path by deriving SQLite
  byte counts from the already-encoded native statement buffer.
- Removed stale root and nested `buildSrc` residues so the repository has one actual shared
  Gradle logic home instead of a live included build plus leftover magic-directory state.
- Replaced deprecated Jackson 3 tree-string APIs in the CLI reader and CLI capability tests so
  local verification stays warning-clean under explicit `-Xlint:deprecation`.

## [0.3.1] - 2026-04-10

### Added
- Added `docs/DEVELOPER_GRADLE.md` as the canonical contributor reference for FinGrind's root
  build, shared build logic, nested Jazzer build, and the invariants that should be reviewed
  before changing Gradle architecture.

### Changed
- Replaced repository-local `buildSrc` usage with one shared `gradle/build-logic` included build
  consumed by both the root product build and the nested Jazzer build, and moved shared pulse,
  managed-SQLite, and Jazzer task logic into typed convention plugins there.
- Moved FinGrind's Jazzer harness and run-target inventory into one committed
  `jazzer-topology.json` contract consumed by both Gradle build logic and Jazzer runtime support
  code, and wired the nested Jazzer build to import the root version catalog instead of hardcoding
  overlapping dependency coordinates.
- Updated developer-facing documentation and README routing so contributors can review the current
  Gradle system with one maintained system map instead of reconstructing it from scripts.

### Fixed
- Removed duplicated managed-SQLite and pulse-listener implementations that had diverged between
  the root and nested Jazzer builds.
- Fixed `./check.sh` stage logging so long-running stages no longer rely on a racy FIFO-based tee
  pipeline that could fail independently of the monitored command.
- Fixed `./check.sh` Java 26 shell validation so valid macOS launcher-stub environments are
  accepted when both `java` and `javac` actually resolve to Java 26.

## [0.3.0] - 2026-04-10

### Changed
- Replaced the SQLite shell-out adapter with an in-process Java 26 FFM adapter that keeps one real
  SQLite handle per opened book store, uses prepared statements through the SQLite C API, and
  performs commit-time duplicate checks inside the SQLite transaction boundary.
- Pinned controlled FinGrind surfaces to a managed SQLite 3.53.0 runtime built from the vendored
  official amalgamation, and removed the remaining reliance on whatever host `libsqlite3` version
  happened to be installed on local or CI machines.
- Updated Docker, CI, release, developer, and user-facing surfaces so Gradle runs, GitHub
  workflows, and the container image all verify and report the managed SQLite 3.53.0 contract.

### Fixed
- Corrected Gradle `api` versus `implementation` declarations so modules that expose core/runtime
  types through their public API compile cleanly through transitive boundaries.
- Made the nested Jazzer build compile and inject the same managed SQLite 3.53.0 runtime used by
  the root Gradle build so local fuzzing and regression replay no longer drift onto an older host
  library.
- Refreshed SQLite integration tests to assert the native-backed session lifecycle, schema
  initialization behavior, and direct failure mapping instead of the deleted subprocess executor.
- Hardened the native SQLite bridge so schema application uses `sqlite3_exec`, ordinary duplicate
  outcomes are decided before insert inside `BEGIN IMMEDIATE`, and bound text no longer relies on
  statement-memory lifetime conventions.
- Refined native SQLite failure shaping so canonical schema loading fails at runtime instead of
  class initialization, and script execution prefers SQLite's exec-owned error text when present.
- Added machine-readable SQLite runtime metadata for managed-versus-system loading, required
  minimum version, ready versus incompatible runtime state, and the loaded SQLite version.
- Tightened request-money parsing so exponent notation is rejected as `invalid-request` instead of
  leaking raw arithmetic overflow from extreme numeric inputs.

## [0.2.0] - 2026-04-10

### Added
- Stable deterministic rejection codes for core posting and correction admission, including
  duplicate idempotency, missing or forbidden correction reason, missing correction target,
  duplicate reversal target, and reversal-shape mismatch.
- Additional Jazzer regression coverage for forbidden committed-audit request fields and the new
  correction rejection paths.

### Changed
- Split caller-supplied request provenance from committed audit metadata. Posting requests no
  longer accept `provenance.recordedAt` or `provenance.sourceChannel`; FinGrind stamps those
  fields at commit time.
- Moved correction linkage out of `JournalEntry` and onto posting commands and committed facts,
  then enforced core correction rules at the application boundary.
- Replaced the versioned bootstrap schema file with one canonical current SQLite schema and made
  preflight against a missing book side-effect free.
- Hardened the SQLite and runtime write boundaries around typed commit outcomes, posting-id lookup,
  and one-reversal-per-target enforcement.

### Fixed
- Aligned the Docker smoke request payload with the current hard-break request contract so the
  containerized release-surface check exercises real `post-entry` success instead of a stale
  caller shape.
- Container publication verification now accepts the formatted JSON emitted by the `version`
  command, preventing false release-workflow failures after the GHCR images themselves were
  already published correctly.

## [0.1.0] - 2026-04-09

### Added
- Initial release.

[Unreleased]: https://github.com/resoltico/FinGrind/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.4.0
[0.3.1]: https://github.com/resoltico/FinGrind/releases/tag/v0.3.1
[0.3.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.3.0
[0.2.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.2.0
[0.1.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.1.0
