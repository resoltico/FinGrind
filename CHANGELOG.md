# Changelog

Notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.10.0] - 2026-04-14

### Changed
- Hard-broke protected-book access to require exactly one explicit passphrase source per
  book-bound command: `--book-key-file`, `--book-passphrase-stdin`, or
  `--book-passphrase-prompt`.
- Reworked the protected-book seam so CLI parsing now models passphrase-source selection
  explicitly while the SQLite adapter opens books from resolved zeroizable UTF-8 passphrase
  material instead of carrying a key-file-only assumption through the storage boundary.

### Fixed
- Added safe non-file passphrase support for humans and pipelines without exposing plaintext book
  secrets through CLI arguments or environment variables.
- Updated machine-readable discovery, Docker smoke verification, Jazzer support flows, and user /
  developer documentation so the protected-book contract no longer drifts back to a file-only
  model.

## [0.9.0] - 2026-04-14

### Changed
- Hard-broke book persistence onto SQLite3 Multiple Ciphers 2.3.3, so every book-bound CLI
  command now requires `--book-key-file` and every newly opened book is protected at rest with the
  upstream default `chacha20` cipher.
- Replaced the vendored plain SQLite amalgamation with the official SQLite3 Multiple Ciphers 2.3.3
  amalgamation, based on SQLite 3.53.0, across the root build, nested Jazzer build, Docker image,
  CLI runtime metadata, and contributor documentation.

### Fixed
- Hardened protected-book key handling by reading explicit UTF-8 key files, rejecting empty or
  malformed key material, stripping one trailing line ending, and zeroizing transient plaintext
  bytes after native handoff.
- Stabilized vendored SQLite3MC source verification across Git checkouts by hashing normalized
  LF line endings instead of host-specific working-tree bytes.
- Codified the local standalone verification rule that `:cli:shadowJar` packages only the Java
  surface and `prepareManagedSqlite` must also run before validating the JAR against the managed
  SQLite3 Multiple Ciphers library.

## [0.8.0] - 2026-04-14

### Changed
- Hard-broke the discovery contract behind `help`, `version`, `capabilities`, and
  `print-request-template` onto application-owned typed descriptors instead of CLI-local map
  assembly, so the machine surface is now generated from one canonical contract source.
- Reworked `capabilities` to publish field descriptors, live enum vocabularies, explicit
  `preflightSemantics: advisory`, and an explicit
  `currencyModel.scope: single-currency-per-entry` with `multiCurrencyStatus: not-supported`.

### Fixed
- Removed machine-contract drift between `CliRequestReader`, `FinGrindCli`, and the public docs by
  sharing request-field names and live rejection catalogs instead of duplicating string lists in
  each layer.

## [0.7.0] - 2026-04-13

### Changed
- Hard-broke book lifecycle and posting admission to require explicit `open-book` initialization
  plus a declared per-book account registry before any `preflight-entry` or `post-entry` can
  succeed, and added `declare-account` and `list-accounts` to the public CLI surface.
- Added `AccountName` and `NormalBalance` to the core model, introduced book-administration result
  and rejection families in `application`, extended the SQLite schema with `book_meta` and
  `account`, and enforced `journal_line.account_code` through a real SQLite foreign key.
- Reworked the committed Jazzer replay contract so posting and SQLite harnesses explicitly assert
  the lifecycle order: unopened-book rejection, undeclared-account rejection,
  inactive-account rejection, then the final success or reversal-policy outcome.
- Updated user and developer documentation, examples, and Docker smoke verification around the
  explicit book/account lifecycle, the managed-versus-host SQLite runtime split, and the current
  operator flow.

### Fixed
- Stopped the packaged CLI from crashing with `ExceptionInInitializerError` when a standalone
  `java -jar` invocation finds an unsupported host `libsqlite3`; FinGrind now returns a structured
  `runtime-failure` surface instead.
- Hardened the Docker smoke gate so it now exercises `open-book`, `declare-account`,
  `list-accounts`, `preflight-entry`, and `post-entry` in the supported order instead of relying
  on the removed implicit-book behavior.

## [0.6.0] - 2026-04-13

### Changed
- Hard-broke the module graph to `core -> application -> sqlite -> cli` by deleting the
  `runtime` module, moving committed posting facts and ordinary commit outcomes into
  `application`, renaming the persistence seam to `BookSession`, and moving the in-memory session
  fixture onto the application test-fixture classpath.
- Switched the default production posting identity from UUID v4 to project-owned UUID v7
  generation, so CLI commits now return time-ordered `postingId` values by default.
- Updated public and developer documentation to describe the new book-session architecture and to
  codify the supported Gradle setup as wrapper-only, local-filesystem-first development.

### Fixed
- Hardened SQLite native failure shaping so stale-handle and close-failure paths use
  handle-independent SQLite error strings instead of dereferencing invalid database handles while
  formatting exceptions.
- Hardened Docker smoke verification on fresh Docker Desktop workstations by running the public
  image flow through an anonymous `DOCKER_CONFIG` while still targeting the active local Docker
  engine, avoiding credential-helper stalls during public base-image resolution.
- Documented the verified workstation constraint that full Gradle and JaCoCo verification belongs
  on the local filesystem; external mounted volumes can fail file-locking requirements during
  project cache or coverage execution.

## [0.5.0] - 2026-04-11

### Changed
- Hardened newly initialized SQLite books by making the canonical `posting_fact` and
  `journal_line` tables `STRICT`, so durable storage now rejects non-lossless type mismatches at
  the SQLite layer instead of relying only on the Java model.
- Standardized the supported Jazzer operator surface around `jazzer/bin/*`, rewrote the
  developer-facing fuzzing and workflow docs to make active fuzzing explicitly local-only, and
  clarified that raw `./gradlew -p jazzer fuzz...` task names are build internals rather than the
  supported live-fuzz interface.

### Fixed
- Opened SQLite book connections now disable `trusted_schema` while keeping `foreign_keys`
  enabled, tightening the runtime trust boundary for agent-facing CLI usage.
- Extended SQLite verification and local Jazzer round-trip assertions so the current hardening
  contract explicitly checks strict-table persistence, pragma configuration, and the committed
  Unicode round-trip seed inventory.
- Active Jazzer harness execution now hard-blocks when `GITHUB_ACTIONS=true`, preloads a
  project-owned premain agent to avoid late Java 26 self-attach behavior, and runs local active
  fuzzing through wrapper-owned `--no-daemon`, run-lock, timeout, and interrupt-cleanup paths.
- Capped `./check.sh` stall diagnostics process capture so a genuinely stuck local stage does not
  fan out into unbounded `jcmd` and `lsof` collection across every descendant process.

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

[Unreleased]: https://github.com/resoltico/FinGrind/compare/v0.10.0...HEAD
[0.10.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.10.0
[0.9.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.9.0
[0.8.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.8.0
[0.7.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.7.0
[0.6.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.6.0
[0.5.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.5.0
[0.4.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.4.0
[0.3.1]: https://github.com/resoltico/FinGrind/releases/tag/v0.3.1
[0.3.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.3.0
[0.2.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.2.0
[0.1.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.1.0
