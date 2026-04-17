# Changelog

Notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.15.0] - 2026-04-17

### Changed
- Hard-broke the product module graph from `core -> application -> sqlite -> cli` into
  `core -> contract -> executor -> sqlite -> cli`, moving all public request/result/metadata types
  and protocol ownership into `contract` while keeping execution services and seams in `executor`.
- Added AI-agent-first ledger plans as a first-class contract and CLI surface through
  `print-plan-template` and `execute-plan`, including ordered plan steps, assertions, atomic
  execution, and durable per-step journals returned to callers.
- Moved public operation metadata into the contract protocol catalog, so operation ids, aliases,
  display labels, output modes, command summaries, hard book-model facts, preflight facts,
  currency facts, status lists, and shared query limits now have one typed owner before `help`,
  `capabilities`, or CLI rendering.
- The release protocol now treats open Dependabot PRs as first-class release hygiene. Release-time
  pre-flight now requires explicitly identifying open Dependabot work, and after the public
  release is verified each Dependabot PR must be merged, closed, or consciously kept open with a
  stated reason; stale automation branches are no longer acceptable release leftovers.
- Split the old monolithic book-session seam into dedicated administration, posting, and query
  interfaces, and added first-class read/query workflows for `inspect-book`, `get-posting`,
  `list-postings`, `account-balance`, and paged `list-accounts`.
- Reworked posting commit flow to reuse one shared validation model across preflight and
  transactional SQLite commit, while deferring UUID v7 `postingId` allocation until the store has
  accepted the write.
- Tightened the core accounting model by introducing `PositiveMoney` for journal lines, leaving
  `Money` as the exact non-negative type used by balances and other zero-capable read models.
- Expanded the public bundle matrix to include `windows-x86_64`, added a first-class
  `bin\fingrind.cmd` launcher plus Windows `.zip` archives, and taught release/container
  automation to wait for and publish the Windows asset set as part of the canonical release
  contract.
- Hardened bundle assembly so requested bundle classifiers must match the active host platform;
  FinGrind no longer allows metadata-only cross-classifier bundle builds that would lie about the
  bundled runtime image or managed SQLite library.

### Fixed
- Added contract lint coverage that fails the build when production Java reauthors operation ids
  outside the contract protocol catalog or when docs/catalog examples mention unregistered operation
  references.
- Fixed `print-plan-template` so the emitted document now matches the accepted `execute-plan`
  request shape, uses the generic `assertion` field instead of a non-existent
  `accountBalanceAssertion`, and includes an initial `open-book` step that lets agents bootstrap a
  brand-new book in one plan.
- Replaced first-failure account admission on posting writes with aggregated
  `account-state-violations`, so callers now receive every undeclared or inactive account issue in
  one deterministic rejection.
- Hardened machine-facing discovery and help metadata to advertise paged account reads,
  compatibility inspection, and the current hard-break format policy explicitly instead of
  implying an unbounded or migration-backed surface.
- Restored the documented `jazzer/bin/*` operator surface, including wrapper-owned lock, log,
  cleanup, and timeout behavior, fixed cleanup tasks so they also succeed on a fresh checkout with
  no prior `.local` state, and added a deterministic Jazzer support test so that wrapper contract
  cannot disappear from the checkout unnoticed.
- Added JSpecify package coverage, updated query/result tests, and refreshed Jazzer fixtures and
  replay support so the new read surface, account-state rejection shape, and positive-amount
  invariant are asserted end to end.
- Added a native Windows managed-SQLite build path using MSVC, updated runtime lookup to resolve
  `sqlite3.dll`, and added Windows-specific smoke verification plus CI coverage for the published
  Windows bundle.
- Pinned Spotless-managed source and project-file verification to LF line endings so
  configuration-cache-enabled Windows CI does not depend on Spotless' platform-default
  line-ending provider.

### Documentation
- Updated README, user guides, examples, developer references, and API parity docs for the
  contract/executor module split, AI-agent ledger plans, `print-plan-template`, `execute-plan`,
  committed plan journals, query commands, paged responses, inspect-book compatibility metadata,
  aggregated account-state rejections, and positive journal-line amounts.
- Documented the contract protocol catalog ownership model and the contract lint expectations that
  keep CLI help, capabilities, docs, and user-facing hints aligned.
- Updated the public distribution, user CLI, and release-protocol docs for Windows x64 bundles,
  Windows `.zip` release assets, the `bin\fingrind.cmd` launcher, and the new Windows bundle
  smoke workflow.

## [0.14.0] - 2026-04-14

### Changed
- Expanded the public self-contained CLI bundle matrix to include `macos-x86_64`, added
  top-level archive bootstrap files (`README.md` and `bundle-manifest.json`), and extended the
  machine-facing environment contract with `runtimeDistribution`,
  `supportedPublicCliBundleTargets`, and `unsupportedPublicCliOperatingSystems`.
- Tightened the private runtime-image policy for both bundles and containers so public
  distributions now use `jlink --compress=zip-6`, fail loud on unresolved module analysis, and
  avoid dragging tool modules into the shipped runtime image.

### Fixed
- Hardened bundle smoke portability on GitHub macOS runners by removing the Bash 4-only
  `mapfile` dependency, so release automation now remains compatible with the runner-provided
  Bash 3.2 shell while asserting the same self-contained bundle contract.
- Brought the public container image onto the same managed-runtime contract as the bundle
  archives by verifying the vendored SQLite3MC source hash during Docker build, shipping a
  trimmed private Java runtime, and making tag-driven container publication wait for the complete
  GitHub release asset set.

## [0.13.0] - 2026-04-14

### Changed
- Hard-broke public CLI distribution from a GitHub-release JAR onto self-contained per-platform
  bundle archives that carry the FinGrind launcher, a private Java 26 runtime image, the managed
  SQLite3MC native library, and release checksums.
- Reworked the machine-facing runtime contract to describe the real public distribution surface
  through `publicCliDistribution`, `sourceCheckoutJava`, and
  `sqliteLibraryBundleHomeSystemProperty`.

### Fixed
- Eliminated the public `fingrind.jar` release mismatch by teaching the SQLite runtime to resolve
  its managed native library from extracted bundle home while preserving the explicit
  `FINGRIND_SQLITE_LIBRARY` override for developer-only raw-JAR work.
- Added first-class bundle packaging and smoke verification to Gradle, `./check.sh`, CI, and the
  GitHub release workflow, so the primary published artifact is now built and asserted directly.
- Fixed bundle smoke archive discovery to target the current host/version bundle deterministically
  instead of failing when older release archives are still present in `cli/build/distributions`.
- Updated the README, user docs, developer docs, and release protocol to codify the bundle-first
  distribution policy, the current public target matrix, the Linux glibc bundle stance, and the
  release-automation use of Zulu 26 for `javac`, `jdeps`, and `jlink`.

## [0.12.0] - 2026-04-14

### Changed
- Added `generate-book-key-file` as the canonical machine-safe secret-file workflow, so FinGrind can
  create one new owner-only key file without ever printing the generated passphrase.
- Hard-broke standalone SQLite runtime discovery onto a managed-only contract in both code and
  machine-facing capabilities metadata.

### Fixed
- Hardened CLI request decoding to reject duplicate JSON object keys, reject unknown fields at every
  object level, and publish those strict request rules through the capabilities surface.
- Hardened passphrase handling further by rejecting embedded control characters across key-file and
  stdin routes, so machine and interactive secret entry stay on one reproducible single-line text
  contract.
- Hardened SQLite book connections to pin `journal_mode=DELETE`, `synchronous=EXTRA`,
  `secure_delete=ON`, `temp_store=MEMORY`, and the existing schema-safety pragmas instead of
  relying on ambient host defaults.
- Hardened Docker smoke verification onto `docker buildx build --load` while preserving anonymous
  `DOCKER_CONFIG` isolation by staging an already-installed host `docker-buildx` plugin into the
  temporary smoke config when the empty config would otherwise hide it, so FinGrind no longer
  falls back to Docker's deprecated legacy builder path.
- Hardened Docker smoke further to discover and reuse an already-installed host `docker-buildx`
  plugin portably, so anonymous-config verification works both on macOS Docker Desktop and on CI
  runners without one fixed plugin path.
- Hardened Docker smoke mounted-path execution further by running container commands as the caller's
  UID:GID, so generated `0600` key files stay readable by the invoking operator on Linux CI as well
  as local macOS Docker Desktop.
- Aligned the GitHub CI, container, and release workflow runtime assertions with the managed-only
  capabilities contract, so publication no longer checks the removed `sqliteLibrarySource` field.
- Removed reflective final-field mutation from the SQLite native-handle failure tests by replacing
  it with package-private native-handle override seams, keeping the suite compatible with Java 26's
  current warning posture and future stricter JDK behavior.
- Updated Docker smoke, Jazzer hardening assertions, README, and user/developer docs so they no
  longer claim unsupported host-library fallback behavior or the old `sqliteLibrarySource` field.

## [0.11.0] - 2026-04-14

### Changed
- Hard-broke protected-book administration again by adding `rekey-book`, enforcing real read-only
  SQLite sessions for `list-accounts` and `preflight-entry`, and stamping initialized books with a
  fixed FinGrind `application_id` plus storage `user_version`.

### Fixed
- Hardened external SQLite runtime acceptance so FinGrind now rejects libraries that miss the
  required SQLite3MC compile-option contract instead of trusting version strings alone.
- Hardened book-key handling further by rejecting non-POSIX or non-owner-only key files, aligning
  Docker smoke fixtures with that same requirement, and documenting the enforced `0400` / `0600`
  secret-file rule.
- Fixed the documented shell operator surface on stock macOS Bash 3.2 under `set -u`, including
  both `./check.sh` and `jazzer/bin/*`, so empty optional-argument arrays no longer abort the
  supported verification and fuzzing entrypoints.

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

[Unreleased]: https://github.com/resoltico/FinGrind/compare/v0.15.0...HEAD
[0.15.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.15.0
[0.14.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.14.0
[0.13.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.13.0
[0.12.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.12.0
[0.11.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.11.0
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
