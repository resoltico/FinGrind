---
afad: "5.0.1"
version: "0.62.2"
domain: CHANGELOG_ARCHIVE_2026_MAY
updated: "2026-08-09"
route:
  keywords: [fingrind, changelog, release notes, archive, history]
  questions: ["where are the archived FinGrind release notes for May 2026"]
---

# Changelog Archive: 2026-05

Historical release notes moved out of the root `CHANGELOG.md` so the repository root stays focused on the current release line and recent history.
Use [CHANGELOG.md](../CHANGELOG.md) for the active release surface.

## [0.30.0] - 2026-05-02

### Changed

- Replaced the application description string with "Command-line double-entry bookkeeping with one
  encrypted book per business" in `gradle.properties` as the single source of truth, propagated
  through `processResources` to the packaged `fingrind.properties` resource, and updated
  `CliMetadataTest` to assert against the packaged value instead of a hardcoded literal so the
  description cannot drift between the build and test layers.
- Overhauled the root `README.md`.
- Added Apache Commons Logging (transitive via PDFBox) to `NOTICE` and `PATENTS.md`;
  added full PDFBox/FontBox sub-attributions required by Apache 2.0 Section 4(d) for the
  Adobe Glyph List, Zapf Dingbats Glyph List, Bidi Mirroring Glyph Property, TwelveMonkeys ImageIO,
  CMYK ICC-profile, and Script Property third-party content embedded in the PDFBox JARs;
  added jackson-core FastDoubleParser attribution with a pointer to the preserved
  `META-INF/FastDoubleParser-NOTICE` and `META-INF/thirdparty-LICENSE` in the distributed JAR;
  corrected PDFBox/FontBox/PDFBox IO copyright year from 2025 to 2026;
  added `LICENSE-SQLITE3MULTIPLECIPHERS` to the shadow JAR `META-INF` so the SQLite3 Multiple
  Ciphers license is accessible in every distribution mode including Docker; added all six root
  legal files to the Docker image at `/opt/fingrind/doc/` and allowlisted them in `.dockerignore`;
  added a legal pointer to the bundle `README.md` template; updated `PATENTS.md` component
  description and table to include Apache Commons Logging.
- Standardized the contributor environment around the Dev Container Specification instead of a
  VS-Code-only mental model, and documented the official tooling-agnostic `devcontainer` CLI path
  plus a noob-safe Docker-only Jazzer session from first terminal prompt through live fuzz output.
- Pinned JaCoCo directly to one exact Java-26-ready snapshot artifact in the version catalog
  as `0.8.15-20260429.155228-97` instead of resolving through the mutable `0.8.15-SNAPSHOT`
  alias, removed the alias-drift sidecar verifier from the release surface, and tightened the
  Gradle coverage wiring so module and aggregated reports both consume every local
  `build/jacoco/*.exec` file produced by every `Test` task.
- Upgraded the shared JUnit BOM to `6.1.0-RC1` and refreshed the developer docs to keep the
  documented test baseline aligned with the build.

### Fixed

- Promoted the protected-book format to one canonical protocol contract, taught discovery to
  publish the full `environment.storage.defaultProtectedBookFormat` object, proved the managed
  SQLite3MC default cipher settings through native introspection, recorded the committed fixture's
  persisted format facts in metadata, added deterministic same-book writer-contention coverage,
  and expanded the SQLite docs to spell out the real encryption boundary around temp storage,
  memory, backups, exports, and colocated key files.
- Extended the canonical Stage 1 quality gate so `./check.sh`, CI, and the new
  `scripts/run-quality-gates.sh` helper now execute the included `gradle/build-logic:test`
  surface alongside root `check coverage`, closing the gap where repository verification plugins
  could drift behind a green top-level gate.
- Field-tested the operator and local-execution surfaces so shell wrappers now return truthful
  `--help` output without falling through to raw Gradle or full acceptance runs, `rekey-book`
  now names replacement-key inputs as existing replacement secret files, ledger-plan shape
  failures now point agents directly at the required nested object, the real interactive
  passphrase prompt path is exercised under pseudo-terminal coverage instead of only the
  no-console branch, malformed request JSON now carries parse-message plus line/column details,
  the generated source-checkout launcher and developer raw JAR now auto-discover the managed
  SQLite runtime from a prepared checkout instead of requiring manual `FINGRIND_SQLITE_LIBRARY`
  setup there, and command help now rewrites its quick-start examples to the active runtime
  surface instead of assuming a bundle-only launcher.
- Increased the tagged container-publication workflow budget so its final public-tag verification
  no longer times out after a successful multi-arch `ghcr.io` push, and added a shell regression
  guard that keeps the workflow's release-asset gate, publication verifier, and timeout contract
  aligned.
- Narrowed the CLI module's Jackson reflection boundary to one dedicated `dev.erst.fingrind.cli.json`
  package instead of opening the whole CLI implementation package, and moved the transport JSON
  record owners into that package so JPMS reflective access now matches the real deserialization
  seam.
- Hardened contributor and verification infrastructure so the devcontainer now repairs root-owned
  cache volumes on start, the validator proves that repair path explicitly, the release protocol
  now documents worktree-safe payload bootstrap and detached merge handoff, and `./check.sh`,
  Docker smoke, devcontainer validation, and Jazzer wrappers all serialize through one repo-wide
  verification lock with repo-scoped Gradle state. Lock reentry for descendant shell and Gradle
  processes now follows published lock-owner metadata instead of fragile parent-PID inference, and
  the shared shell/Python verification paths also redirect Python bytecode caches into system temp
  so checks no longer leave `__pycache__` residue in the repository tree.
- Hardened SQLite bootstrap and rekey handling so the process-global `strlen` lookup stays lazy,
  active-connection counter underflow now fails fast instead of silently suppressing shutdown, and
  rekey-owned passphrases now follow Java's `AutoCloseable` resource contract directly.
- Rebuilt the internal SQLite session seam around one immutable store context plus one mutable
  lifecycle owner with a durable session-secret collaborator, so close failures now end the session
  decisively, read views route through the focused read operations, and discovery preserves resolved
  SQLite runtime facts when late probe work fails.
- Journal-entry validation now accumulates every detected grammar violation into one deterministic
  failure, the core API publishes that aggregated failure through
  `JournalEntryValidationException`, and CLI `invalid-request` responses now expose those ordered
  violations structurally under `details.violations`.
- Hardened the Jazzer operator surface so deterministic local and CI-safe verification now runs
  through `jazzer/bin/test`, `jazzer/bin/regression`, and `jazzer/bin/check` instead of raw
  nested-Gradle commands, the wrapper/regression surfaces derive target keys from the committed
  Jazzer topology document instead of booting the nested build just to enumerate wrappers, and the
  Java replay/list-findings/regression entrypoints require an explicit `--project-root` contract
  rather than inferring the project from caller cwd. Those deterministic wrapper entrypoints now
  also start from a clean relocated nested-build output so removed inner classes cannot survive
  across sessions and poison JaCoCo verification.
- Hardened the nested Jazzer Gradle build so `compileJava` prunes its cached main source-set
  output directory before recompiling. Direct `./gradlew --project-dir jazzer ...` runs no longer
  carry orphaned helper classfiles forward into JaCoCo or deterministic replay after a source file
  deletes nested types.
- Fixed the remaining Jazzer and release-surface rough edges so `./check.sh --help` and
  `./scripts/bundle-smoke.sh --help` and `./scripts/docker-smoke.sh --help` exit before
  Python/bootstrap or temp-directory work, the operator-help regression now proves those help
  paths leave no temp residue behind, `jazzer/README.md` plus local GitHub-block messages now
  point humans and agents at the wrapper-owned Jazzer commands instead of obsolete raw
  nested-Gradle invocations, `JazzerCli` now uses the same positional replay/list-findings
  grammar as `jazzer/bin/replay` and `jazzer/bin/list-findings`, `jazzer/bin/list-findings`
  renders text and JSON from one replay pass instead of reclassifying every raw artifact twice,
  the public `NOTICE` file now matches the checkout-managed raw-JAR SQLite runtime contract, and
  the pinned Jazzer JVMs now opt into `--sun-misc-unsafe-memory-access=allow` plus `-Xshare:off`
  so Java 26 verification no longer emits terminal `sun.misc.Unsafe` or bootstrap-classpath CDS
  warnings from the upstream fuzzing stack.
- Field-tested the packaged, source-checkout, raw-JAR, and Jazzer operator surfaces again so the
  developer raw-JAR quick start now prints a real `java -jar` command instead of a bare Java
  version label, command-specific CLI argument failures now point directly at `help <command>`
  instead of only the global help index, `jazzer/bin/replay --json` and
  `jazzer/bin/list-findings --json` now emit machine-clean JSON without Gradle task chatter, and
  `./scripts/bundle-smoke.sh` now reports which bundle archive it exercised when multiple local
  archives are present.
- Fixed bundle archive ownership so `:cli:bundleCliArchive` no longer leaves obsolete
  `cli/build/distributions/fingrind-*` archives and checksum files behind after repeated local
  packaging runs, and added a regression that seeds stale artifacts and proves the real build task
  prunes them before writing the current host bundle.
- Tightened the remaining SQLite/Jazzer/operator ownership seams so SQLite book sessions and native
  handles now reject cross-thread access explicitly instead of only documenting thread confinement,
  Bash release-smoke support files now fail fast when executed directly instead of returning a
  false-green no-op, and Jazzer wrapper target discovery plus replay/list-findings validation now
  project directly from the committed topology document instead of spawning the nested Gradle build
  for target enumeration.
- Expanded the committed `sqlite-book-roundtrip` Jazzer surface so parsed SQLite seeds now also
  drive executed read/report response rendering, corrupt pre-schema book-path failures,
  concurrent contender behavior, and derived reversal near misses and duplicate reversals, and
  collapsed the Jazzer open-gap register onto one canonical coverage document.
- Split the SQLite round-trip Jazzer helper into focused rendering, lifecycle, derivation,
  concurrency-outcome, and resource owners with matching focused proof classes, renamed the Stage
  5 release-surface gate to `scripts/check-release-surface-scripts.sh`, and stopped
  `jazzer/bin/replay`, `jazzer/bin/list-findings`, and local Jazzer cleanup wrappers from wiping
  nested build outputs before read-only inspection or maintenance runs.
- Collapsed the in-memory posting-workflow Jazzer invariant surface onto one shared owner used by
  both fuzz and replay, removed the duplicate replay verifier, and added direct invariant proofs
  so the local coverage gate now enforces one committed posting-workflow theory instead of two
  drifting copies.
- Fixed the remaining Stage 5 and Jazzer replay operator rough edges so
  `scripts/check-release-surface-scripts.sh --help` now exits through a real side-effect-free help
  path, the operator-help regression now guards that public Stage 5 entrypoint too, replay input
  paths now fail at the wrapper or direct-CLI boundary with one command-owned diagnostic instead of
  shell `cd` errors or Java `NoSuchFileException` stacktraces, and held repo-verification locks no
  longer mislabel valid Jazzer targets as unknown or claim that no active harnesses exist.
- Reworked the SQLite concurrent-writer Jazzer coverage so encrypted-session setup is serialized
  before the contested commit race begins and timed-out worker cleanup now uses explicit daemon
  executor cancellation, preventing the Stage 2 Jazzer gate from wedging indefinitely inside the
  concurrent round-trip proof.

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
- Fixed the Jazzer deterministic verification surface so GitHub Actions no longer flips
  harness-runner tests through ambient `GITHUB_ACTIONS` state, and deterministic pulse logs now
  report truthful completed-class progress instead of a brittle precomputed total.

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
  placeholders instead of hardcoded operator/agent metadata, and documented the single-use
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
  specialized owners instead of carrying the full implementation inline in multiple public wrappers or
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
- Jazzer wrapper timeboxing now starts from the libFuzzer start marker instead of raw Gradle
  process launch, so bounded local fuzz sessions no longer get mislabeled as timeout failures just
  because startup and instrumentation took longer than the requested fuzzing window. Wrapper exit
  `124` is now reserved for real timeout teardown, while `jazzer/bin/fuzz-all` keeps that
  distinction when it stops on the first actionable harness failure and prints replay-classified
  findings for the failed target. The wrapper and its regression surface also stay compatible with
  stock macOS Bash 3.2 while deriving the active harness list from `jazzer-topology.json`.
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
- The then-existing local Jazzer cleanup wrappers traversed local run state without descending into
  preserved corpus subtrees, and downgraded undeletable corpus remnants to explicit warnings
  instead of aborting the cleanup command. The root `spotless` project-file sweep now also excludes
  ignored `.local/` runtime state so one unreadable local corpus cannot poison `./check.sh`.
- Added `scripts/verify-public-container-surface.sh` plus mock-backed shell regression coverage,
  and updated the release protocol to use that deterministic operator-side verifier so public
  container checks now assert machine-readable `version --output json`, exact text
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

[0.30.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.30.0
[0.29.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.29.0
[0.28.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.28.0
[0.27.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.27.0
[0.26.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.26.0
[0.25.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.25.0
[0.24.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.24.0
[0.23.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.23.0
