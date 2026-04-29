# Changelog

Notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.29.0] - 2026-04-29

### Changed

- Removed the public `BookMigrationPolicy` contract surface and the passive SQLite migration
  placeholder types, and moved the `USER_CLI` command-table sync launcher out of production
  sources so the public contract now exposes only real book-format facts and runtime surfaces.
- Added a committed contributor devcontainer surface, a CI validation job for it, and release
  merge-handoff/tag verifiers that now treat `Contributor devcontainer` as release-blocking even
  though GitHub branch protection still protects only `Check`, `Windows bundle smoke`, and
  `Docker smoke`.
- Split the public help quick-start contract into surface-keyed POSIX-shell and Windows-PowerShell
  workflows with canonical launcher commands plus explicit file-write steps instead of one
  flattened shell transcript, and moved the Gradle wrapper onto the stable `9.5.0` line while
  keeping the JVM 26 build baseline intact.
- Replaced the fake one-value `SourceChannel` enum with a singleton contract owner so the current
  public line records the durable committed-entry surface truthfully without pretending it already
  has an extensible source-channel taxonomy.

### Fixed

- Fixed the public help/discovery surface so `help <command>` and `<command> --help` now return
  scoped command usage/examples, bundle-launched repair hints and scoped help rewrite to the real
  extracted launcher path instead of bare `fingrind`, and local bundle restaging prunes stale
  `cli/build/bundle/fingrind-*` roots instead of leaving old versioned bundle trees behind.
- Removed the stale tracked `gradle/build-logic/bin/` shadow tree, added canonical domain
  invariants for `CurrencyBalance`, `AccountCode`, and `IdempotencyKey`, and derived the machine
  request schemas from those same identifier owners so contract docs and runtime validation no
  longer drift.
- Fixed the remaining request/inspection/storage drift so `inspect-book` now reports
  `canInitializeWithOpenBook` truthfully for missing book paths, `execute-plan` maps begin,
  commit, and rollback failures into structured rejected journals instead of leaking raw
  exceptions, and the SQLite schema now enforces the same account-code and idempotency-key
  identifier contract as the Java/domain and machine-schema surfaces.
- Fixed SQLite session and mutation seams so public `SqliteBookSessions.open(...)` variants now
  prime their sessions, non-key-file same-package access rejects through typed `ContractDecision`
  failures, account reactivation updates the persisted `declaredAt` timestamp consistently, and
  unexpected ledger-plan runtime failures are journaled as structured step-failure rejections
  instead of escaping unchecked.
- Fixed SQLite/native diagnostics so more primary and extended result codes now surface stable
  names, negative book-state snapshots are rejected at construction time, and the CI workflow now
  runs deterministic nested Jazzer coverage instead of leaving that regression surface local-only.
- Fixed the Gradle/JaCoCo toolchain surface so FinGrind now resolves the upstream Java 26-ready
  JaCoCo snapshot build 0.8.15.202604281210 through the real Maven alias 0.8.15-SNAPSHOT, and
  added a release-surface verifier that fails if that mutable alias drifts away from the
  repository's pinned timestamped snapshot artifact.
- Fixed plan-journal truth and storage-boundary drift so unexpected begin, initialization-check,
  commit, and rollback failures now end `execute-plan` with explicit `plan-boundary` journal
  entries, the canonical SQLite schema rejects blank persisted account/provenance identifiers, and
  the protocol plus developer docs no longer describe a removed migration planner or sequential
  in-place migration policy.
- Fixed the committed source-channel contract so SQLite schema proofs, fixtures, and schema docs
  now tie the persisted `source_channel` value directly to the canonical `SourceChannel.CLI`
  owner instead of carrying free-floating `'CLI'` test literals.

## [0.28.0] - 2026-04-28

### Changed

- Upgraded the Gradle wrapper to `9.5.0-rc-4`, moved the shared `gradle/build-logic` surface onto
  Kotlin `2.4.0-Beta2`, and aligned the included build with the Java 26 baseline so it now emits
  JVM 26 bytecode instead of carrying a separate JVM 25 exception.
- Updated the user and developer guides so the canonical request/plan scaffold semantics, current
  Gradle/Kotlin baseline, and Java 26 build-logic contract stay aligned with the live CLI and
  build surfaces.

### Fixed

- Fixed interactive prompt passphrase handling so malformed UTF-16 input is rejected instead of
  being silently replacement-encoded into a different protected-book secret, and added regression
  coverage for both the SQLite passphrase adapter and the CLI prompt resolver.
- Fixed the request and ledger-plan scaffold boundary so canonical `replace-before-commit-*`
  provenance sentinels are now rejected before any posting or plan can reach durable state, and
  added direct parser plus live workflow regression coverage for the raw template path.
- Fixed the canonical request and plan scaffolds so they now publish agent-owned provenance
  placeholders instead of hardcoded human/operator metadata, and documented the single-use
  `idempotencyKey` contract around those templates.
- Fixed the canonical request and plan scaffolds so `effectiveDate` is now an explicit
  replace-before-submit placeholder instead of a stale concrete date, and aligned the
  protocol/docs guidance with that scaffold contract.
- Fixed request-validation recovery hints so `execute-plan` now points callers at
  `print-plan-template`, account-declaration failures no longer point at the posting scaffold, and
  the canonical help workflow now includes the required template-edit step instead of implying raw
  scaffolds are directly runnable.
- Fixed request-file transport failures so missing or unreadable `--request-file` payloads now
  surface path-aware diagnostics instead of being mislabeled as generic JSON parse failures, and
  fixed the canonical `help`/docs quick-start workflow so the required account-declaration JSON
  files are created explicitly rather than assumed to exist.
- Fixed the shell-side contract reader and bundle/release smoke verifiers so they derive the
  source-checkout Java baseline, default cipher, and managed-SQLite version contract from the
  canonical protocol resources instead of carrying duplicate verifier-owned literals.
- Fixed GitHub Actions so the canonical Stage 5 release-surface shell-script gate now runs in CI
  instead of relying only on local `./check.sh` executions.
- Added live CLI contract tests for the published quick-start and example workflows so the public
  guides now fail fast when their commands, fixtures, or scaffold guidance drift away from the
  executable surface.
- Fixed the managed SQLite contract so source id and required compile options now have one
  canonical owner, runtime discovery reports provenance plus loaded-library/source-id details, and
  bundle/source-checkout smoke verifiers assert those same facts instead of checking only version
  strings.
- Fixed SQLite protected-book handling so book files and present sidecars are hardened to
  owner-only permissions on supported filesystems, `rekey-book` preserves and restores a rollback
  copy when replacement-secret verification fails, and encrypted-book tests now prove obvious
  sentinel plaintext does not leak to the raw database bytes.
- Fixed public secret-handling examples so stdin passphrase workflows no longer embed passphrase
  literals directly on the shell command line; the docs and canonical protocol examples now use
  file-fed or prompt-based routes instead.
- Fixed SQLite runtime discovery so `capabilities` now distinguishes managed-library compile-option
  failure from plain "not-verified", and the bundle/source-checkout smoke verifiers enforce that
  explicit runtime verdict.
- Added committed encrypted protected-book fixtures plus closed-book backup/restore regression
  coverage, and documented the supported operator backup contract as one protected `.sqlite` file
  copy plus later file replacement while the book is closed.
- Added failure-path regressions proving CLI and SQLite error surfaces do not echo prompt, stdin,
  key-file, or replacement-secret contents when protected-book authentication fails.

## [0.27.0] - 2026-04-26

### Changed

- Public request/plan template fixtures are now deterministic canonical scaffold captures with the
  fixed example `effectiveDate` value `2026-04-17`, and the user/reference docs now describe them
  as exact `print-request-template` / `print-plan-template` outputs instead of current-date shape
  examples.
- CLI bundle assembly now renders `bundle-manifest.json` as valid generated JSON from the canonical
  distribution contract during staging, and the developer distribution docs now record that
  generation path explicitly.
- `DistributionContractReader` and the ledger-plan machine-contract schema builder now delegate to
  smaller role-owned collaborators for contract paths, JSON loading, schema loading, host-platform
  normalization, field-set assembly, and variant-schema synthesis instead of keeping those seams in
  two mixed-responsibility god-files.
- `docs/USER_CLI.md` now keeps its command table in a generated contract-owned block rendered from
  the canonical protocol catalog instead of hand-maintaining public command rows separately.
- The root `check.sh`, Unix release-smoke entrypoints, and Windows bundle-smoke entrypoint now
  delegate monitoring and release-surface workflow logic through dedicated support modules and a
  shared Python office-worker workflow package with focused config, CLI, fixture, assertion, and
  phase owners instead of carrying the full implementation inline in multiple public wrappers or
  replacement god-files.
- The shared release-smoke workflow now derives bundle/container fixture paths from the compact
  canonical environment tuple `FINGRIND_RELEASE_SMOKE_WORK_ROOT`,
  `FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE`, and `FINGRIND_RELEASE_SMOKE_SCENARIO_ID`, so the
  Bash bundle verifier, Docker verifier, and Windows PowerShell verifier no longer re-author large
  parallel per-path environment maps at the wrapper seam.

### Fixed

- Fixed the canonical protocol catalog so fixed-stdout commands such as `print-request-template`,
  `print-plan-template`, and `execute-plan` no longer advertise selectable JSON output modes they
  do not support; CLI help and docs now distinguish fixed raw JSON from fixed JSON envelopes.
- Fixed the `capabilities` machine contract so grouped `commands` entries now publish the
  authoritative per-command `executionMode`, `outputModes`, and `artifactOutputs`, and
  `requestInput` now publishes only the canonical `outputOption` selector instead of a false global
  `queryOutputModes` contract.
- Fixed the canonical protocol catalog so every command that advertises selectable output modes now
  also includes the matching `--output` syntax in its canonical option list, and pinned that seam
  with contract tests.
- Fixed the generated `docs/USER_CLI.md` command table so it preserves the exact canonical option
  spellings from the protocol catalog, including raw `|`-delimited variants inside generated HTML
  code cells, and added the `:contract:syncUserCliDocs` sync task so the generated block is
  materially refreshed from the canonical owner instead of only test-compared.
- Fixed the root `check.sh` stage contract so the fixed stage inventory, Stage 5 shell-regression
  list, and help text now derive from one shared owner instead of being re-authored separately in
  comments, usage output, and execution flow.
- Fixed the root `check.sh` stage contract so the stage-to-command execution wiring now also
  delegates through the canonical stage owner instead of keeping a second fixed-stage case map in
  the root script.
- Fixed the shared release-surface office-worker acceptance seam so the Bash bundle verifier, Bash
  Docker verifier, and Windows PowerShell bundle verifier now delegate their common command,
  fixture, and assertion workflow through one Python owner instead of maintaining parallel
  near-copied implementations.
- Fixed the shared operation-id contract so shell-side consumers now read the full explicit
  semantic-key registry from the protocol schema resource instead of inferring most semantic keys
  by camel-casing enum names.
- Fixed the Windows bundle acceptance seam so the PowerShell entrypoint once again keeps Unicode
  workspace-path coverage alive through `workspace odd/Rīga büro/...`, and pinned that surface
  with cross-shell regression checks.
- Fixed the published Windows PowerShell bundle launcher so Unicode-only path characters such as
  `ī` now reach the bundled Java runtime through a `ProcessStartInfo.ArgumentList` launch path
  instead of degrading into invalid `?` path characters during release-smoke execution.
- Fixed the remaining Windows bundle launcher Unicode seam so staged bridge arguments now stay in
  a UTF-8 JSON file until the JVM resolves them through `FINGRIND_LAUNCHER_ARGUMENTS_FILE`,
  instead of being rehydrated in PowerShell and pushed back across a second native argv boundary.
- Fixed bundle and Docker smoke verification so pagination cursors are read from JSON structurally
  instead of with regex text scraping, which keeps release acceptance aligned with the actual JSON
  contract.
- Fixed bundle, Docker, and Windows bundle smoke verification so the release-surface checks now
  validate the report stdout/PDF contract from structured per-command descriptors instead of a
  duplicated global `queryOutputModes` assumption.
- Fixed the Gradle test feedback loop so repo-owned script and documentation contract tests now
  declare those repo files as task inputs, preventing `:cli:test` and `:contract:test` from going
  `UP-TO-DATE` after shell/doc drift that the assertions are supposed to catch.
- Fixed SQLite best-effort cleanup so rollback, close, delete, and runtime shutdown failures now
  emit observable warnings and test hooks instead of disappearing silently.

## [0.26.0] - 2026-04-25

### Changed

- Refreshed the build-quality toolchain to PMD 7.24.0 so root and nested Jazzer Java verification
  now run on the newer PMD release line.
- Root `AGENTS.md` plus `.codex/**` are now repo-owned tracked metadata instead of repo-ignored
  local scratch, while `.gitattributes` marks both surfaces `export-ignore` so GitHub source
  archives still match the public distribution boundary.
- Bundle manifests, bundle launchers, Docker entrypoints, shell smoke verifiers, and build logic
  now derive runtime-distribution, storage, and public-distribution facts from the canonical
  protocol contract resources instead of maintaining parallel literal registries.
- Bundle layout and managed-SQLite version pins now live in dedicated protocol-owned JSON contract
  resources, so Gradle build logic, bundle metadata, SQLite runtime checks, and shell verifiers all
  consume the same per-target launcher/archive/native-library facts and pinned native-version
  contract instead of separate platform lookup tables or Gradle properties.
- Protocol operation-id, public-distribution, runtime-surface, and generated runtime-environment
  facts now live in shared JSON contract resources, so runtime loaders, build logic, and shell
  verifiers consume the same canonical contract format instead of carrying parallel `.properties`
  parsers.
- Runtime-distribution, storage, protected-book, and managed-SQLite discovery metadata now flow
  through canonical typed wire vocabularies instead of open strings, and the request-shape
  contract now distinguishes truly conditional nested ledger-plan fields from globally optional
  ones.
- Machine-readable discovery payloads now keep command ids, execution modes, and output modes
  typed through the canonical protocol enums, and request/plan templates keep actor, side, step,
  assertion, and balance vocabularies typed instead of flattening them to raw strings.
- Public response envelopes, response-model descriptors, ledger-plan execution semantics, and the
  bundle-target discovery matrix now publish typed status, failure-policy, transaction-mode, and
  bundle-target vocabularies instead of open strings, and the environment contract now names
  `unsupportedPublicCliBundleTargets` accurately as host classifiers instead of implying raw OS
  ids.
- Build logic now reads protocol contract schema keys from the shared JSON schema-key resource, so
  runtime loaders and Gradle distribution assembly no longer maintain parallel owners for external
  contract field/property names.
- Root Spotless project-file coverage now includes tracked `.codex/**` Markdown, so repo-owned
  agent/system-theory files are back under the default repository hygiene gate.
- `jazzer/bin/fuzz-all` now keeps pure per-harness timeout exits moving, but stops on the first
  actionable harness failure and prints replay-classified findings for that target before
  returning, so real bugs no longer get buried beneath later harness logs. The wrapper and its
  regression surface now also stay compatible with stock macOS Bash 3.2 while deriving the active
  harness list from `jazzer-topology.json`.
- Jazzer replay expectations, finding artifacts, and JSON/operator output now use the typed
  lower-case wire vocabularies they actually model instead of flattening sealed outcomes and
  lifecycle states back to ad hoc strings.
- The nested Jazzer build now applies the shared Java conventions gate stack, so Spotless,
  Error Prone, NullAway, PMD, JaCoCo, and the shared source/Jackson policy tasks cover replay
  tooling and deterministic tests too.
- Jazzer fuzz harnesses now run under an explicit fuzz-specific PMD profile, so the nested build
  keeps real structural and correctness checks on fuzz code without misclassifying single-method
  `@FuzzTest` harness classes as empty JUnit suites.

### Fixed

- Fixed the Windows bundle smoke verifier so PowerShell list comparisons no longer crash on
  singleton-or-empty `Compare-Object` results, and added a Stage 5 PowerShell regression so that
  cross-platform shell checks catch the seam before Windows CI becomes the first detector again.
- Fixed the generated Docker entrypoint to use the POSIX shell provided by the Alpine runtime
  image instead of a Bash shebang the image does not ship, so container acceptance now exercises
  the real published entrypoint surface instead of failing before Java starts.
- Fixed the bundle README and machine-readable bundle manifest so they now publish the canonical
  managed SQLite and SQLite3 Multiple Ciphers version pins from the shared protocol contract
  instead of hardcoding version text or omitting those bundle bootstrap facts entirely.
- Added first-class `jazzer/bin/replay` and `jazzer/bin/list-findings` operator commands backed by
  FinGrind's deterministic replay seam, and corrected the Jazzer docs so raw libFuzzer artifact
  prefixes are no longer described as authoritative bug classifications before replay proves that
  they reproduce as `unexpected-failure`.
- Fixed the ledger-plan Jazzer assertion layer so rejected `list-accounts` and `list-postings`
  steps no longer demand success-only pagination facts, and promoted the missing-book
  `list-postings` reproducer into the committed regression seed floor with replay metadata.
- Replaced the flat Jazzer replay detail god records with parsed-request, lifecycle, outcome, and
  plan-shape subrecords, added explicit unparsed-input detail variants, and stopped fabricating
  rejected ledger-plan execution snapshots on unexpected failures.
- Restored one canonical owner for the duplicated protocol wire-field names `accountCode`,
  `currencyCode`, `effectiveDateFrom`, and `effectiveDateTo`, and pinned the aliases with
  contract tests so future drift fails fast.
- Duplicate machine-contract schema keys are now hard failures instead of silent rightmost wins,
  and ledger-plan discovery/schema coverage is derived from the canonical step/assertion enums so
  new variants cannot compile without updating both the executable schema and the agent-facing
  discovery contract.
- `print-plan-template` now publishes a dedicated nested query template descriptor and rejects
  structurally impossible ledger-plan step/assertion combinations before they reach users,
  documentation, or agent tooling.
- `scripts/verify-github-release.sh` now verifies GitHub-generated zipball and tarball source
  archives in addition to release metadata and named assets, and the repo keeps a dedicated shell
  regression plus archive-level contract tests for the `export-ignore` boundary.
- The repo-owned metadata tracking gate now proves `AGENTS.md` and `.codex/**` are present in
  `HEAD`, not merely staged in the index, so preservation failures cannot slip past `./check.sh`.
- Fixed the build-logic plugin classpath wiring to use typed version-catalog plugin accessors while
  still compiling the shared Spotless and Error Prone convention code.
- Fixed the Bash and PowerShell bundle smoke gates so they derive host archive, launcher, native
  library, and manifest version expectations from the shared contract reader instead of hardcoded
  Windows/x86_64 assumptions or a bespoke `.properties` parser.

## [0.25.0] - 2026-04-23

### Changed

- `capabilities.requestShapes` now publishes executable JSON Schema documents alongside the
  existing field-descriptor arrays, so agents and external tooling can consume one authoritative
  machine contract instead of re-implementing validation from prose.
- The executable machine-contract schema builder is now split into focused posting,
  declare-account, ledger-plan, and shared-support collaborators instead of one cross-domain
  god-file.
- The public SQLite session seam now accepts the contract-level `BookAccess` tuple together with
  `SqlitePassphraseResolver` and `SqlitePassphraseIntent`, and `rekey-book` follows that same
  safe source-resolution contract instead of exposing adapter-native secret objects at the public
  boundary.

### Fixed

- `open-book` now creates missing parent directories consistently even when the default SQLite CLI
  workflow primes a create-capable session before initialization, so nested `--book-file` paths
  work with key-file, stdin, and interactive-prompt passphrase sources instead of leaking
  `SQLITE_CANTOPEN`.
- Missing-book CLI workflows now preserve deterministic `administration-book-not-initialized`,
  `query-book-not-initialized`, and `posting-book-not-initialized` outcomes instead of leaking
  SQLite `runtime-failure` opens when the selected book file does not exist.
- `execute-plan` now keeps structured success facts for declared accounts, balance assertions, and
  list-query steps, including row groups and pagination state, instead of collapsing plan query
  outcomes to bare counters.
- Runtime CLI failures are now classified as `managed-runtime-failure`,
  `storage-runtime-failure`, `pdf-export-failure`, or `runtime-failure` as appropriate, instead of
  collapsing all thrown runtime problems into one coarse public code.
- The split CLI command records now keep package-private constructors and an immutable parser
  registry, and the remaining `SqlitePostingFactStore` pass-through overrides were collapsed into
  `SqliteStoreContext`, so the refactor no longer carries PMD-hostile adapter shell layers.
- The deterministic Jazzer ledger-plan harness now executes parsed plans against the in-memory
  ledger-plan service, and the committed seed set includes a successful list-query plan that pins
  structured journal facts.
- `jazzer/bin/clean-local-findings` and `jazzer/bin/clean-local-corpus` now traverse local run
  state without descending into preserved corpus subtrees, and they downgrade undeletable corpus
  remnants to explicit warnings instead of aborting the cleanup command. The root `spotless`
  project-file sweep now also excludes ignored `.local/` runtime state so one unreadable local
  corpus cannot poison `./check.sh`.
- Added `scripts/verify-public-container-surface.sh` plus mock-backed shell regression coverage,
  and updated the release protocol to use that deterministic operator-side verifier so public
  container checks now assert machine-readable `version --output json`, exact human
  trial-balance rows, and PDF output instead of relying on ambiguous ad hoc terminal parsing.

### Documentation

- Replaced machine-specific absolute Markdown links in contributor and Jazzer docs with portable
  relative or home-path references.
- Clarified the documented Docker smoke gate stage, ledger-plan list-query defaults, and SQLite
  lazy-open versus missing-book semantics so the second-pass docs now match the live CLI parser
  and store lifecycle behavior exactly.
- Refreshed the checked-in ledger-plan response fixtures from live bundle runs, added a runnable
  structured-query plan example, and updated the user/docs index guides for executable request
  schemas plus the split runtime-failure vocabulary.
- Corrected the SQLite architecture docs so they now describe `SqlitePostingFactStore` as the thin
  wrapper it is after the lifecycle/context collapse, and so they route storage failures to the
  current `storage-runtime-failure` / `managed-runtime-failure` taxonomy instead of the old single
  `runtime-failure` bucket.
- Tightened the release protocol so a dirty primary checkout with the intended release payload now
  has an explicit recovery path when Step 1 first merges a release-critical PR and changes
  `origin/main` underneath the pending release work.

## [0.24.0] - 2026-04-23

### Changed
- Refactored ledger-plan journal typing so assertion detail is now owned structurally by
  `LedgerJournalStep` instead of being propagated through `Optional` record components on
  `LedgerJournalEntry`.
- Hard-broke the public SQLite entrypoint down to `SqliteBookSession`,
  `SqliteBookSessionMode`, and `SqliteBookSessions`, keeping `SqlitePostingFactStore` and the
  store-lifecycle collaborators as package-private implementation detail instead of exported
  adapter surface.

### Fixed
- Removed the remaining SQLite production test backdoors by replacing global native-handle
  overrides and `src/main` test-access shims with same-package injected native API seams and
  test-owned helpers.
- Added a canonical `scripts/prepare-release-version.sh` helper plus regression coverage, and
  updated the release protocol to require that scripted version sweep instead of ad hoc
  hand-edits across docs, changelog, examples, and version-pinned tests.
- Updated the release protocol and its shell regression coverage so oversized release PRs now fall
  back from `gh pr diff --name-only` to GitHub's paginated pull-files API instead of stalling on
  `PullRequest.diff too_large`.
- Reworked `gradlew.bat` to use a simpler argument scan, cmd-native Windows project-cache key,
  dedicated setup subroutines, and correct JVM-vs-Gradle argument placement instead of
  parser-fragile inline substitutions, early block-expanded variables, misplaced
  `--project-cache-dir`, and cross-drive temp-cache defaults, and added a dedicated local
  regression so the wrapper stays on the working drive and fails in a named guard before release
  time.
- Fixed the SQLite native close-retry test doubles so successful retries now delegate to the real
  native close instead of only pretending to succeed, which keeps Windows temp-book cleanup from
  depending on Unix-style unlink behavior during CI.
- Made `scripts/docker-smoke.sh` refresh `:cli:shadowJar` and sync relocated Docker build inputs
  back into the repository-visible build context on fragile mounted filesystems, so release
  version bumps and other Docker-surface changes cannot silently reuse stale local container
  inputs from an older checkout build.
- Made `./gradlew` and the nested Jazzer build self-relocate per-checkout project cache,
  build-logic output, JaCoCo execution data, and mounted-checkout project `build/` trees into the
  wrapper-owned local cache when the checkout lives on a fragile network filesystem, so full
  verification and live fuzzing now work from `smbfs` and similar mounts without in-repo cleanup
  failures.
- Moved the release-checkout and Docker smoke regression scratch trees out of the repository and
  made Docker smoke cleanup retry-and-warn instead of escalating to interactive `sudo`, so
  mounted-workspace tombstones no longer poison later `check.sh` stage-1 runs after successful
  acceptance verification.
- Changed report-command `--pdf-out` handling so successful primary report results stay on stdout
  even when the optional PDF artifact later fails; those artifact failures now surface as
  diagnostics warnings instead of converting the whole command into `runtime-failure`.
- Split the remaining CLI and SQLite god-test buckets into behavior-named suites with shared
  support bases, replacing monolithic `FinGrindCliTest` / `SqlitePostingFactStoreTest` coverage
  sinks with narrower discovery, workflow, lifecycle, query, and commit seams.
- Broke the last oversized CLI request/argument and SQLite native/store verification buckets into
  narrower suites such as `CliPostEntryRequestReader*`, `Cli*ArgumentParsing*`,
  `SqliteNative*`, and `SqliteBookRekeyAndValidationTest`, and extracted the fake filesystem
  scaffold behind `SqliteBookKeyFileSecurity*` into dedicated fixture support so those tests now
  read as behavior-owned suites instead of mixed behavior-plus-infrastructure god files.
- Split the remaining large CLI workflow/response-writer, contract protocol-lint, and SQLite
  reporting/runtime-probe suites into behavior-owned files, and replaced the single fake key-file
  security filesystem helper with dedicated `TestAcl*` support classes so the last oversized test
  buckets now fail in narrower, directly named seams.
- Tightened SQLite native-handle lifecycle safety so closed database handles now fail fast before
  re-entering FFM code, while store transaction cleanup and commit error translation still report
  operation-scoped failures consistently.
- Centralized `WireValue` enum parsing and vocabulary ownership so stable machine tokens are cached
  and validated once instead of being reimplemented as repeated linear scans across enums.
- Gated `Windows bundle smoke` on `Check` in CI, aligned Gradle wrapper validation to the same
  `gradle/actions` release train, and removed the redundant `inspect-book.payload.initialized`
  field from the machine JSON surface and checked-in examples.
- Corrected the documentation spine so `WireValue` now documents its shared parsing helpers, the
  SQLite adapter docs describe the current store-context and session-view composition, and the
  template guides stop implying that checked-in `print-*template` fixtures are byte-stable across
  changing current dates.

## [0.23.0] - 2026-04-22

### Changed
- Introduced an explicit exported `WireValue` contract for stable FinGrind enum tokens and moved
  CLI JSON serialization onto that compile-time interface instead of reflective `wireValue()`
  lookup.
- Split the remaining flat SQLite native bridge façade into role-owned bootstrap, connection,
  statement, error, and runtime-policy collaborators, so the storage adapter no longer routes
  every native call through one pass-through namespace.

### Fixed
- Switched interactive console passphrase prompting onto the typed JDK `Console` API instead of a
  reflective `Object` seam, while keeping deterministic CLI failures for unavailable or failed
  prompts.
- Restored explicit `--release` targeting to product and Jazzer Java compilation, re-enabled
  incremental shared build-logic compilation, and tightened test-pulse shutdown ordering so the
  verification build is both stricter and less wasteful.
- Updated the published docs so the API reference, SQLite schema notes, ledger-plan contract,
  and rekey/passphrase guidance all match the current wire-value contract, sealed step surface,
  and bundle/runtime behavior.

## [0.22.0] - 2026-04-22

### Changed
- Switched `list-accounts` to the same opaque cursor-based keyset pagination model already used by
  `list-postings`, so account-registry reads now accept `--cursor` and return `nextCursor`
  instead of the older `offset` / `hasMore` paging shape.
- Refreshed release-critical dependencies on the shipped build and container paths, including
  NullAway `0.13.4` and Alpine `3.23`.

### Fixed
- Corrected the release/bootstrap documentation and generated bundle metadata so the documented
  required GitHub checks, release-workflow lookup commands, shipped legal files, Windows launcher
  inventory, and patent notes now match the actual current CI, bundle, and dependency surfaces.
- The release closeout protocol is now executable instead of prose-only. FinGrind now ships
  `scripts/verify-release-primary-checkout.sh`, a dedicated shell regression for it, and updated
  release docs/check wiring so releasing from a disposable worktree cannot quietly leave the
  primary checkout behind `origin/main` with stale version-bearing files and misleading overlays.
- Hardened the live release procedure further so it now explicitly handles in-place release
  candidates, re-runs the full gate after version sweeps, and refreshes sibling dependency-PR
  state after each merge instead of relying on stale GitHub mergeability snapshots.
- Reworked the protocol/build internals around canonical build metadata, explicit discovery
  descriptor types, and narrower CLI/SQLite seams, and refreshed the checked-in docs/examples so
  the published developer and machine-facing guidance matches the current runtime and paging
  contracts.

## [0.21.0] - 2026-04-22

### Changed
- Renamed the remaining generic internal `Support` seams, SQLite reader collaborators, Jazzer
  deterministic-test pulse listener, and related docs so the codebase now uses role-owned names
  consistently instead of catch-all helper terminology.

### Fixed
- Aligned `check.sh`, Jazzer build pulses, and developer docs around the canonical
  deterministic-tests pulse vocabulary, so the local full gate and the documented operator
  surface now describe the same Jazzer verification phases.
- Replaced the inline workflow-only managed SQLite runtime probes with one canonical
  source-checkout verifier script, and bound that same helper into the local root gate so
  GitHub workflow checks cannot drift behind the live `capabilities` contract again.
- Hardened the bundle smoke Java-runtime probes on Unix and Windows to parse the Java major
  version token from combined `java --version` output instead of assuming one exact raw line,
  preventing CI-only bundle false negatives when the runtime reports the same version text
  differently.
- Reworked `check.sh` stall diagnostics and timeout teardown around a shared process-tree helper,
  so bounded `jcmd` and `lsof` probes can no longer outlive the watchdog shell as orphaned
  descendants; the root gate now executes a dedicated TERM-ignoring process-tree regression to
  keep that cleanup contract from drifting.
- Closed the remaining review-driven contract, CLI, and SQLite verification gaps by making
  SQLite store opening an explicit ownership-transfer seam, removing impossible interactive
  prompt null branches, and asserting deterministic `generate-book-key-file` failure and
  passphrase/key-file edge flows through the regression suite.
- Realigned the bundle smoke, Docker smoke, CI runtime-contract verifiers, and packaged CLI docs
  with the nested `capabilities.commands` and
  `capabilities.environment.distribution|storage|sqlite` schema plus the current exit-code
  contract, so public-distribution acceptance checks no longer drift behind the published machine
  contract.
- Reworked the quick-start and example guides so public bundle users no longer depend on
  repo-local `docs/examples/` paths, and tightened contributor documentation to keep bundle-safe
  walkthroughs and source-checkout review fixtures clearly separated.
- Fixed the live `help` quick-start examples and packaged CLI docs so they now point at
  bundle-safe local request files instead of repo-only `docs/examples/` paths that do not exist
  inside extracted public release archives.

## [0.20.0] - 2026-04-21

### Changed
- Split the package-private CLI JSON transport model monolith into explicit administration,
  envelope, query, report, plan, and rejection model families, so the CLI transport surface no
  longer depends on one 500-line god-file for unrelated response shapes.
- Split remaining mixed-responsibility contract, CLI, and Jazzer seams further, including dedicated
  machine-contract request helpers, narrower CLI mutation/discovery/runtime helpers, dedicated
  posting-rejection descriptors, dedicated Jazzer request-vs-posting-vs-SQLite replay
  collaborators, and an owned replay scratch-directory seam instead of one catch-all posting
  replay file.
- Hardened record invariants across CLI, contract, SQLite, and Jazzer model types so non-blank
  textual identifiers are normalized at the constructor boundary and collection-bearing records
  coalesce `null` inputs to immutable empty collections before defensive copying.

### Fixed
- Added missing compact constructors and blank-string validation to replay details, CLI payload
  models, ledger facts, PDF table columns, and SQLite native API metadata.
- Reworked remaining behavior-shaping flag seams in CLI, SQLite, and Jazzer support so pretty JSON
  rendering, dynamic posting/report SQL selection, and SQLite fuzz account state changes now flow
  through explicit methods or query-owned inputs instead of boolean mode switches.
- Stopped Jazzer SQLite replay cleanup from swallowing temporary-directory deletion failures; those
  cleanup faults now surface as real unexpected replay failures instead of silently leaking scratch
  state.
- Corrected the Jazzer developer references to match the real committed regression floor, including
  the fifth `ledger-plan-request` seed for the 100-step protocol limit rejection.

### Documentation
- Split the application reference docs into narrower protocol/discovery, administration/reporting,
  and posting/ledger-plan files, refreshed checked-in examples from live current behavior, and
  realigned the developer documentation with the current root build, bundle, Docker, and Windows
  smoke surfaces.

## [0.19.0] - 2026-04-21

### Changed
- Derived the capabilities `sourceCheckoutJava` value from a generated protocol resource wired to
  the canonical Gradle Java-version property, so the machine contract no longer duplicates the
  source-checkout baseline as a hardcoded CLI string.
- Replaced the CLI's duplicated command-failure exception handling with a sealed
  `CliCommandException` seam and centralized unsupported-output-mode messaging, so failure
  dispatch is exhaustive and the public `--output` option token is consumed through one canonical
  protocol owner.
- Broke up more CLI and SQLite god-files into narrower seams, including dedicated read-query and
  report argument parsers, dedicated query/report human and CSV renderers, and a top-level
  `SqliteStoreAccessMode` contract instead of a nested store-owned access-policy enum.
- Split more SQLite adapter responsibilities into focused helpers, including top-level native
  runtime support, native invocation/error handling, store transaction/failure support, session
  views, transaction validation, store-owned database wrappers, explicit passphrase ownership
  wrappers, dedicated store read/mutation operation coordinators, and separate native bootstrap,
  statement, and error helpers, reducing the remaining monolith pressure in the native bridge and
  book store.
- Split more cross-cutting contract, executor, and CLI god-files into explicit concern seams,
  including dedicated machine-contract request/response/domain descriptor builders, dedicated CLI
  rejection/book/report/plan payload mappers, dedicated ledger-plan assertion and outcome helpers,
  and explicit SQLite store lifecycle and native-connection coordinators.
- Split the remaining mixed SQLite and CLI seams further by introducing a canonical
  `SqliteBookContract`, dedicated SQLite query-vs-report read helpers, dedicated account-vs-summary
  report CLI parsers, a dedicated native-API loader, and a same-package `SqliteStoreTestAccess`
  shim so test-only lifecycle seams no longer bloat the production store façade.
- Hard-broke the executor session ownership model so `BookAdministrationSession`,
  `PostingBookSession`, and `BookReadSession` are now non-owning operation views while the outer
  workflow or store remains the sole lifecycle owner, removing the old aliasing trap where closing
  one narrowed view silently closed sibling views backed by the same store.
- Reworked the parsed CLI command model into structural output-mode subfamilies and added missing
  invariant checks for query/report command records, so failure-output behavior is derived once per
  command family instead of repeated across nearly every command variant.
- Made the public bundle manifest a generated artifact instead of a hand-authored template shadow,
  so bundle bootstrap metadata now points at the canonical `help`, `capabilities`,
  `print-request-template`, and `print-plan-template` operations without maintaining a second
  command registry next to the protocol catalog.

### Fixed
- Preserved JVM `Error` propagation across the SQLite FFM bridge while still wrapping ordinary
  reflective/native invocation failures with deterministic state, so heap or VM failures no longer
  masquerade as storage-classified SQLite problems.
- Moved typed SQLite `MethodHandle` adapters into a non-exported internal package, added the
  required null-marked package boundary, and kept best-effort native shutdown cleanup quiet for
  ordinary bridge exceptions without swallowing JVM `Error`s.
- Removed the remaining magic SQLite result-code literals and kept runtime/library lookup messages
  aligned with the real Windows launcher surface (`bin\\fingrind.ps1` with `bin\\fingrind.cmd`
  retained as a compatibility wrapper).
- Reworked the SQLite store's session-owned connection and rekey secret handling so the strict PMD
  resource rules and the 100% JaCoCo branch gate are both satisfied structurally rather than by
  suppressions or coverage-shaped code.
- Removed the remaining production and Jazzer wildcard imports, production `@SuppressWarnings`,
  production `catch (Throwable)`, and reflective `setAccessible(...)` bridge probes that were
  still violating the repository's AGENTS-guided source policy, and hardened the build logic so
  those regressions now fail fast again.
- Replaced brittle SQLite test reflection that reached into moved private helpers and fields with
  same-package test seams on native connection, store lifecycle, and passphrase internals, so the
  architecture can keep evolving without silently invalidating the regression suite.
- Removed the last uncovered SQLite coverage-shaped branches by deleting unused native/bootstrap
  pass-throughs, normalizing reopened-database cleanup paths, and adding regression coverage for
  declare-account CLI argument rejection, standalone JSON emission, and SQLite cleanup-close
  failures during native connection setup.
- Removed the remaining raw embedded operation ids from CLI, contract, executor, and SQLite
  user-facing messages, and strengthened contract linting so hyphenated command ids embedded inside
  larger string literals now fail the build instead of drifting silently.

## [0.18.0] - 2026-04-19

### Changed
- Added first-class office-worker reporting commands through `trial-balance`, `account-ledger`,
  and `period-summary`, taught the CLI read/report surface to render canonical `json`, `human`,
  and `csv` output modes from the same report models, and added explicit `--pdf-out` export for
  report artifacts through the new report PDF adapter module backed by Apache PDFBox.
- Extended the public CLI output contract so administration and write commands that already carried
  machine envelopes can now also render operator-facing `--output human`, and deterministic
  failures on those commands now stay in the selected human format instead of falling back to JSON.
- Hardened the public verification surface so the bundle, Windows bundle, Docker image, and root
  `./check.sh` flow now run office-worker acceptance workflows instead of only narrow posting smoke
  checks.

### Fixed
- Reclassified deterministic operator-repairable failures onto contract-owned CLI error codes, so
  malformed posting cursors, wrong book passphrases, prompt-unavailable paths, key-file overwrite
  refusals, and invalid key-file contract violations now exit `2` instead of surfacing as generic
  `runtime-failure`.
- Stopped wrong-passphrase failures from leaking raw SQLite storage symptoms such as
  `SQLITE_NOTADB`; the public surface now returns `book-authentication-failed` with repair hints.
- Normalized report JSON payloads onto explicit wire shapes so report commands no longer leak
  internal value-object structure such as nested `.value` wrappers into the machine contract.
- Unified the bundle and container private-runtime build paths around one staged module list and
  explicitly retained `jdk.unsupported`, so Docker can no longer drift from the bundle `jdeps`
  result and PDF export no longer emits PDFBox unmapper warnings on trimmed runtimes.
- Switched the public Windows bundle launcher contract to `bin\fingrind.ps1` and kept
  `bin\fingrind.cmd` as a compatibility wrapper, so Unicode workspace and book paths no longer
  degrade into invalid `?` path characters before the JVM sees them.

### Documentation
- Updated README, user guides, developer docs, release protocol, and checked-in examples for the
  new report commands, output modes, deterministic CLI error taxonomy, and public acceptance
  verification workflow.

## [0.17.0] - 2026-04-18

### Changed
- Changed `list-postings` pagination from offset scans to opaque cursor-based keyset paging, so
  posting-history reads now return `nextCursor` instead of `offset` / `hasMore` and can resume
  without rescanning earlier history pages.
- Restored `BookMigrationPolicy` to a closed enum vocabulary with explicit wire-value helpers, so
  the migration-policy contract remains exhaustively switchable while preserving the same stable
  sequential-in-place public value.
- Tightened repository verification so every Java source set now fails on wildcard imports and
  every product or Jazzer build fails on direct Jackson dependencies outside the single approved
  tools.jackson.core:jackson-databind entrypoint.
- Clarified the repository-wide Jackson rule: FinGrind uses the upstream Jackson 3 databind
  entrypoint while intentionally keeping the `com.fasterxml.jackson.annotation` source namespace
  that Jackson 3 still resolves through its BOM, and regression tests now pin that behavior.

### Fixed
- Added the durable `posting_fact_by_effective_recorded_posting` SQLite index and tightened the
  account upsert SQL so posting-history keyset scans are index-backed and account redeclarations can
  no longer overwrite immutable `normalBalance` or original declaration timestamps at the storage
  layer.
- Added the durable `journal_line_by_account_code` SQLite index and bulk account lookups for
  posting validation, reducing repeated scans for account-balance reads and multi-line posting
  admission checks.
- Cached interpreted SQLite book-state metadata inside one opened store session so repeated
  inspection, validation, and query calls no longer re-run the same PRAGMA and schema probes.
- Updated the SQLite schema reference, user docs, examples, and application API docs to reflect
  the full current schema, cursor-based posting-history pagination, and the current machine-facing
  response shapes.
- Normalized direct posting lineage onto the same record-based sealed-family style as reversal
  lineage, made direct query-session reads consistently require initialized books, and hardened the
  SQLite close/rekey paths so native close failures preserve retryable handles instead of silently
  discarding session state.
- Exposed canonical missing-book rejection codes without dummy record allocation, and documented
  the Jackson dependency-entrypoint policy so the build, docs, and source tree all enforce the same
  no-ambiguity rule.

## [0.16.0] - 2026-04-18

### Changed
- Removed the unused ledger-plan `executionPolicy` request block; plan execution is now advertised
  through core-owned capability metadata as atomic, halt-on-first-failure, and complete-journal.
- Changed successful `execute-plan` envelopes to use `status: "plan-committed"` and changed plan
  journals to emit canonical step `kind` values plus assertion `detailKind`, and moved
  assertion requests to the explicit `kind: "assert"` plus nested assertion `kind` shape.
- Changed plan rejection envelopes to use `status: "plan-rejected"` or
  `status: "plan-assertion-failed"` and map plan journals through explicit CLI wire payloads
  instead of Jackson-serializing domain records directly.
- Changed book-inspection states and plan/step journal statuses to explicit stable wire
  vocabularies, and made plan facts a sealed text/flag/count family while keeping JSON fact
  values typed as strings, booleans, or integers.
- Changed plan-journal facts again to emit explicit wire `kind` metadata plus nested grouped facts,
  so repeated machine observations such as per-currency balances and account-state violations no
  longer depend on positional interpretation.
- Tightened record invariants across posting/query/plan contracts so `Optional<T>` components no
  longer silently accept `null`; callers must pass explicit `Optional.empty()` for absence.
- Added a dedicated ledger-plan Jazzer harness, wrapper, and committed seed floor covering valid
  plan parsing, removed execution-policy rejection, open-book ordering, and unknown step-kind
  error shaping, plus an oversize-plan seed for the 100-step protocol limit.
- Split machine-contract discovery DTOs into focused `ContractDiscovery`, `ContractTemplates`,
  `ContractRequestShapes`, and `ContractResponse` namespaces, leaving `MachineContract` as a pure
  assembler over protocol-owned metadata.
- Promoted public bundle targets and unsupported operating systems into one shared
  `PublicDistributionContract` consumed by both build logic and capabilities metadata, and updated
  tests to assert against the protocol-owned distribution contract instead of local copies.
- Moved reversal reasons out of `provenance` and into typed reversal lineage, so direct postings no
  longer carry reversal-only data and reversal requests now require `reversal.reason` at the
  request boundary.

### Fixed
- Centralized rejection prose for CLI envelopes and plan journals so failed plan steps now report
  actionable messages and compact facts instead of Java class names.
- Aligned book-creation detection around `LedgerPlan.beginsWithOpenBook()` and made `open-book`
  valid only as the first step in a plan.
- Fixed missing-book `execute-plan` runs without an initial `open-book` step to return the same
  deterministic plan rejection shape and exit code whether the selected SQLite file is absent or
  merely uninitialized.
- Removed the duplicate account lookup from account-balance queries by making the query seam return
  an optional balance snapshot for undeclared accounts.
- Buffered CLI JSON rendering before writing to stdout so serialization failures cannot corrupt the
  output stream with partial JSON followed by a second envelope.
- Split the concrete SQLite store off the narrow administration/posting/query seam interfaces by
  returning dedicated session views instead of having one adapter type masquerade as every seam at
  once.
- Removed plan-session inheritance across administration, posting, and query concerns; the atomic
  plan seam now exposes narrow operation views plus explicit transaction methods.
- Bounded `execute-plan` to 100 steps so complete plan-journal responses remain structurally
  limited, and added catalog linting for duplicate operation ids and aliases.
- Made the GitHub Release workflow publish step explicitly run under Bash so Windows bundle assets
  use the same release-upload script semantics as macOS and Linux assets.
- Replaced the SQLite adapter monolith's inlined statement, state, read, write, and open-config
  internals with focused collaborators, removing coverage-shaped manual close helpers from the
  production store implementation while keeping the same durable behavior.
- Updated public docs, examples, and Jazzer regression assets to describe the sequential in-place
  book migration policy and the current reversal request shape without stale rejection codes.

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
- Extended secure book-key files to Windows by enforcing owner-only ACLs alongside POSIX
  `0400`/`0600` permissions, so the Windows bundle supports the same key-file workflow as
  macOS and Linux without weakening secret-file checks while still letting the owner rewrite and
  delete generated key files.
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
  compatibility inspection, and the current sequential in-place book-format policy explicitly instead of
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
- Made contract lint and key-file fixture tests platform-deterministic on Windows by removing
  slash-sensitive source exclusions and by creating secure test key files through the production
  generator path.
- Covered POSIX permission, Windows ACL, and cleanup-failure key-file branches through
  platform-neutral fixtures, keeping the strict coverage gate meaningful on every runner.
- Hardened the Windows bundle smoke script under PowerShell strict mode by normalizing singleton
  file and JSON collections before counting them and by writing UTF-8 fixtures through a stable
  .NET helper instead of host-specific `Set-Content -Encoding` variants.
- Made the Windows bundle smoke script use literal path semantics for dynamic filesystem checks, so
  the intentional bracketed smoke-test filenames no longer become PowerShell wildcard patterns.
- Corrected the Windows bundle smoke wrong-key assertion to verify FinGrind's public
  top-level `runtime-failure` envelope and the expected `SQLITE_NOTADB` storage diagnostic.
- Made native-library path assertions platform-native, so Windows CI verifies managed SQLite
  lookup without relying on POSIX path separators.
- Pinned Spotless-managed source and project-file verification to LF line endings so
  configuration-cache-enabled Windows CI does not depend on Spotless' platform-default
  line-ending provider.
- Closed native SQLite handles on failed open/configuration/validation paths, preventing Windows
  from retaining database-file locks after wrong-key, failed-rekey, or failed-open workflows.

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
  `supportedPublicCliBundleTargets`, and `unsupportedPublicCliBundleTargets`.
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
- Aligned the Docker smoke request payload with the current request contract so the
  containerized release-surface check exercises real `post-entry` success instead of a stale
  caller shape.
- Container publication verification now accepts the formatted JSON emitted by the `version`
  command, preventing false release-workflow failures after the GHCR images themselves were
  already published correctly.

## [0.1.0] - 2026-04-09

### Added
- Initial release.

[Unreleased]: https://github.com/resoltico/FinGrind/compare/v0.29.0...HEAD
[0.29.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.29.0
[0.28.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.28.0
[0.27.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.27.0
[0.26.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.26.0
[0.25.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.25.0
[0.24.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.24.0
[0.23.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.23.0
[0.22.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.22.0
[0.21.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.21.0
[0.20.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.20.0
[0.19.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.19.0
[0.18.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.18.0
[0.17.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.17.0
[0.16.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.16.0
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
