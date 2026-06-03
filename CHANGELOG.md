# Changelog

Notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.51.0] - 2026-06-03

### Changed

- Bumped the release-workflow artifact staging actions to the current Node24-backed pins.
  `release.yml` now uses `actions/upload-artifact` `v7.0.1` and
  `actions/download-artifact` `v8.0.1`, and the release-workflow contract test now locks those
  publication-staging pins so deprecated Node20 runtime drift cannot quietly re-enter the release
  path.
- Bumped the repository-owned Python lint pin to Ruff `0.15.15`, the included-build Kotlin pin
  to the stable `2.4.0` GA line, and the pinned JaCoCo snapshot artifact contract to build
  `0.8.15.202606030734` resolving as `0.8.15-20260603.073432-117`.
- Hard-broke book identity, doctrine, and initialization onto one explicit built-in bookkeeping
  profile. Initialized books now persist and publish the accounting-kernel profile, accounting
  basis, framework posture, entity form, and seeded book template together, while `open-book`
  now initializes the owner-managed service starter chart as the canonical first-run book shape.
- Hard-broke bookkeeping operation language toward one narrower cash-kernel write model. Public
  write flows now treat the seeded owner-managed service chart as the canonical operating shape,
  use one structured `OPEN_ACCOUNTING_POSITION` workflow for initial balances, and reserve the
  remaining direct administrative path for explicit reversal adjustments instead of the broader
  retired opening-balance and manual-adjustment vocabulary.
- Hard-broke the public workflow and request boundary onto narrower contract owners. Public
  interaction limits now live at the protocol boundary instead of in the accounting core,
  ledger-plan parsing rejects recognized-but-illegal branch fields deterministically, CLI
  workflow facts now use canonical wire-value owners instead of raw discriminator strings, and
  the root verification surface now owns the Jazzer gate as a first-class repository check.
- Hard-broke operator discovery and help toward one cleaner storefront. `help` now stays
  operator-first, terminal JSON defaults to pretty-printed output, discovery focuses own distinct
  detail ladders instead of coarse overfetch-only tiers, and request and report guidance now
  branch from the shortest runnable path before deeper machine-contract material.
- Hard-broke starter-chart guidance and checked-in public examples into one coherent progression.
  The seeded owner-managed service chart remains the canonical first-run path, while extra
  declaration fixtures are now named and documented as supplemental starter-chart extensions
  rather than alternate zero-state starters.
- The self-contained public CLI bundle is now the primary Gradle `assemble` outcome. `assemble`
  now owns the bundle archive manifest that names the emitted archive and checksum paths, while
  the raw shadow JAR remains an internal packaging input rather than the primary distributable.
- Release publication now stages one Linux container image per supported public bundle target and
  promotes the verified staging images onto the public version and `latest` tags only after the
  staged release surface passes verification. Historical public GHCR package versions are no
  longer culled by a timestamp cutoff during release publication.
- Hard-broke structural governance farther across the repository control plane. PMD now fails
  god-class, method-count, complexity, and coupling violations; source-shape budgets fail
  oversized Java files; duplication checks reject large repeated translation-heavy blocks;
  reviewed structural inventory now owns every near-ceiling production Java surface; and Python
  support scripts and SQLite schema SQL now sit under the same structural governance surface.
  Markdown docs and Gradle build scripts now live under executable structural budgets too, reviewed
  waivers expire against frozen approved shape, CLI JSON fallback budgets are tighter, and managed
  SQLite runtime consumers now opt in explicitly through the dedicated
  `dev.erst.fingrind.managed-sqlite-consumer` plugin instead of path heuristics.
- Hard-broke the build and publication control plane onto manifest-owned, immutable release
  surfaces. GitHub release publication now stages and verifies draft assets before final
  promotion, while bundle and container publication share one manifest-owned archive and staging
  surface instead of log-scraped or checkout-mirrored handoff paths.
- Hard-broke managed SQLite packaging and container assembly onto one shared provenance-owning
  pipeline. The public bundle and the public container now derive their managed SQLite library,
  checksum, toolchain fingerprint, and build contract from the same Gradle-owned native surface,
  with Linux-target staging verified before Docker image assembly.
- Hard-broke the included-build control plane into narrower capability owners. Root conventions
  now delegate formatting, Python/SQL verification, coverage, and Jazzer wiring to focused
  collaborators, while Java conventions now delegate runtime, quality, and coverage wiring
  instead of carrying those concerns inline.

### Fixed

- Fixed GitHub Release publication so public tagged assets are immutable after promotion.
  Publication now verifies staged bundles, checksums, attestations, and public container surfaces
  before the release is finalized instead of deleting and replacing already public assets under
  the same tag.
- Fixed operator-surface discovery and reporting output so human help no longer interleaves
  machine-reference blocks with first-run guidance, primary query examples no longer force early
  artifact export, report and PDF identity context render one concept per row instead of
  slash-packed summaries, and response-contract discovery now exposes a meaningfully smaller
  compact slice rather than mirroring the full surface.
- Fixed starter-workflow example cohesion so checked-in posting examples, plan examples, starter
  chart language, and supplemental declaration examples now describe the same live seeded chart
  instead of mixing starter and extension flows as if they were interchangeable.
- Fixed book-administration validation so duplicate active result-holding declarations fail at
  declaration time, with the public rejection surface naming the singular-account invariant
  directly instead of surfacing that conflict only later during period close.
- Fixed passphrase-source handling so prompt, stdin, and file-backed secret bytes now flow through
  one zeroizing limit-owning path instead of separate partial readers with different cleanup
  behavior.
- Fixed SQLite runtime, bootstrap, and protected-book fixture truth so managed-runtime inspection,
  source-checkout runtime verification, staged protected-book fixtures, and public compatibility
  metadata all agree on the current format-owned contract.
- Fixed SQLite verification task hygiene so ordinary `:sqlite:test` and `:sqlite:pmdTest` runs
  no longer regenerate the committed protected-book fixture family inside source-controlled test
  resources. Protected-book fixture refresh now remains an explicit maintenance task instead of a
  release-gate side effect.
- Fixed Docker publication staging so Linux hosts no longer short-circuit container assembly onto
  the source-checkout managed SQLite runtime when the host and container classifiers match. The
  staged Docker context now remains target-toolchain-owned, and container acceptance now carries
  explicit target-architecture wiring for reproducible local and hosted verification.
- Fixed the Jazzer replay and regression-corpus surface so replay wrappers tolerate real
  verification-lock cleanup timing and the committed corpus follows the live request and
  ledger-plan grammar, starter-chart accounts, and workflow semantics instead of retired fields.

## [0.50.0] - 2026-06-01

### Changed

- Bumped the current release-control and formatting dependency pins to the presently accepted
  mainline set. The CI and container workflows now use `docker/setup-buildx-action` `v4.1.0`,
  `docker/setup-qemu-action` `v4.1.0`, `docker/login-action` `v4.2.0`, and
  `docker/metadata-action` `v6.1.0`, while Gradle build tooling now pins `com.gradleup.shadow`
  `9.4.2` and `com.diffplug.spotless` `8.6.0`.
- Hard-broke structural governance into repository-owned seams instead of filename-privilege
  exceptions. PMD now fails god-class, method-count, complexity, and coupling violations;
  source-shape budgets fail oversized Java files; duplication checks reject large repeated
  translation-heavy blocks; reviewed structural inventory now owns every near-ceiling production Java surface;
  and Python support scripts and SQLite schema SQL now sit under the same structural governance surface
  instead of living outside the mechanical governance model.
- Hard-broke several large control-plane and bookkeeping-adjacent ownership seams to match that
  governance model. CLI command families, protected-book maintenance, SQLite posting SQL, SQLite
  native/result-code seams, contract discovery metadata, and template-validation owners now flow
  through narrower responsibility-held collaborators instead of broader mixed-purpose files.
- Hard-broke operator discovery and report exports toward one clearer public contract. Help text
  now leads with a runnable zero-state lifecycle that includes key-file and request-scaffold
  creation, request-file command help now shows scaffold-plus-run examples in the primary `Try It`
  path, machine discovery can be narrowed by focus and command category instead of forcing one
  coarse payload tier, and CSV outputs now publish explicit export-family, row-identity, and
  relation semantics instead of command-local ad hoc dialects.
- Hard-broke PDF and empty-scope report presentation toward answer-first surfaces. Short PDF
  reports now lead with the statement tables and compress context into a lighter metadata band,
  empty read/report text no longer borrows fictive posting dates, and list-account output now
  separates financial-position and profit-or-loss classifications instead of collapsing them into
  one overloaded human column.
- Hard-broke initialized-book identity and bookkeeping-kernel policy selection onto one explicit
  executable accounting-kernel owner. Book identity now persists and publishes the built-in
  country-agnostic-bookkeeping-kernel profile, protected-book format `22` stores that owner
  durably, and SQLite fixtures, protocol facts, request/inspection payloads, examples, and docs
  now agree on the same profile-bearing book boundary.
- Hard-broke period close from a caller-assembled sequence into one atomic capability with durable
  contiguous close-horizon enforcement. Period-result transfer now commits through one owned
  transfer surface, while ledger-plan JSON models reject impossible close/result shapes before
  execution instead of relying on deeper executor failure paths.
- Hard-broke the managed SQLite runtime identity surface to one sidecar-consistency contract.
  Bundle-managed and source-checkout-managed runtimes now verify against one sibling `.sha256`
  file, the machine-readable runtime trust basis names match that public contract, and Windows
  bundle archives now publish the canonical `bin\fingrind.ps1` launcher without the retired
  `bin\fingrind.cmd` compatibility wrapper.
- Hard-broke runtime-image and container assembly onto explicit reproducibility owners. Runtime
  module derivation now validates optional `jdeps` misses against one repo-owned allowlist before
  module closure is computed, and the Docker build now pins both base images and Alpine package
  revisions instead of floating on mutable tags or repository package updates.

### Fixed

- Fixed SQLite runtime/bootstrap truth surfaces so managed-runtime unavailability, native bootstrap
  failures, shutdown faults, and source-checkout runtime inspection now preserve causality and
  publish the current runtime contract instead of collapsing into looser failure shapes.
- Fixed release, protocol, example, and operator documentation drift so the current structural
  governance surface, accounting-kernel profile ownership, protected-book format `22`, and live
  request/inspection/report contracts now agree across checked-in docs, examples, fixtures, and
  verification scripts.
- Fixed public discovery and validation truth so top-level help no longer presumes missing key or
  request files, query/report discovery summaries now use the current book horizon language
  instead of the retired latest-posting wording, and ledger-plan date repair hints preserve the
  dotted request path in their corrective guidance.
- Fixed protected-book maintenance target handling so `backup-book` and `restore-book` now own
  missing nested output parent directories with owner-only protection, and the bundle/container
  acceptance workflows prove those maintenance paths from the shipped public surfaces instead of
  pre-seeding insecure destination directories.
- Fixed late-cycle release-control regressions so the contract-operation lint no longer falls over
  on large escaped source literals, SQLite key-file security coverage is proven directly across
  platform filesystem capability seams, and the Jazzer replay wrapper regression now tolerates
  brief repo-lock cleanup windows for non-lock contract paths inside the Stage 5 shell gate.

## [0.49.0] - 2026-05-28

### Changed

- Hard-broke structural governance beyond production Java code. God-file, duplication, and
  ownership pressure now applies across Gradle build logic, release shell surfaces, production
  Java, and test Java, with oversized CLI, contract, SQLite, and verifier helpers split into
  smaller responsibility-owned seams instead of broad utility files.
- Hard-broke the release-control plane into narrower owners. Release-smoke contract checks,
  structural-governance verification, launcher verification, and public-container verification now
  live under focused repo-owned script and helper surfaces instead of larger mixed-purpose test and
  wrapper files.
- Hard-broke bookkeeping statement doctrine out of the technical `executor.bookkeeping.read`
  slice. Financial-position, income-statement, and changes-in-equity computation now live under
  `executor.bookkeeping.reporting`, while `BookkeepingReadService` remains an orchestrator over
  query and reporting outcomes.
- Hard-broke operator discovery and help toward one clearer front door. `help --output text` is
  now the task-first operator surface, `capabilities --output text` is the factual inventory
  surface, and command help now prefers one direct runnable example before scaffolds, machine
  schemas, and deeper contract detail.
- Hard-broke the protected-book secret seam back to one tighter SQLite package boundary. Key-file,
  passphrase, and resolver types now live under `dev.erst.fingrind.sqlite`, the exported
  `dev.erst.fingrind.sqlite.secret` package is gone, raw passphrase-byte copy helpers are no
  longer public, and the internal native bridge is split into narrower purpose-owned collaborators
  instead of wider raw-handle reachability.
- Bumped the Gradle build-logic Kotlin pin from `2.4.0-RC` to `2.4.0-RC2`.

### Fixed

- Fixed the public-container verification contract so the mounted `trial-balance --output text`
  check now proves the current published section surface instead of an older pre-refactor header
  ordering. The verifier and its regression harness now assert the active `Trial Balance`,
  status, totals, accounts, and context blocks independently, matching the shipped container
  output used by release publication.
- Fixed `--pdf-out` command semantics so PDF artifact creation is now part of the requested
  operation. Report commands fail with deterministic `pdf-export-failure` instead of succeeding
  with one warning when the requested PDF cannot be written.
- Fixed discovery category serialization so machine-readable help and capabilities now publish
  stable `OperationCategory` wire values instead of deriving protocol strings from Java enum
  constant names.
- Fixed SQLite text decoding so exact-length UTF-8 values survive embedded-NUL round trips instead
  of truncating at the first zero byte during readback.
- Fixed the canonical repository check entrypoint so product verification now defers to the
  project-owned Gradle toolchain baseline instead of rejecting environments based on ambient
  `java` / `javac` product versions before the wrapper can apply the build contract.
- Fixed the front-door and supporting docs so the README quick start, operator guides, request
  guides, developer guides, and report examples now match the live trial-balance surface, current
  PDF failure semantics, current Java-toolchain ownership, and the moved bookkeeping reporting
  package.
- Fixed empty-scope read and report surfaces so no-result list pages are result-first and CSV
  empties now publish one explicit granularity vocabulary: scope-empty for list/query scopes,
  section-empty for empty report sections, and report-empty for whole-report absences.

## [0.48.0] - 2026-05-27

### Changed

- Hard-broke the machine request contract onto one shared owner for request-field inventories.
  Posting, declare-account, and ledger-plan parsers now read their accepted field sets from the
  same contract-owned vocabulary that discovery publishes, and ledger-plan date bounds now enforce
  the same canonical `YYYY-MM-DD` grammar as the rest of the machine request surface.
- Hard-broke the CLI failure boundary so uncategorized software defects no longer publish one
  generic runtime message. Public machine envelopes now expose the opaque `internal-error` code
  with one error id, while full stack traces move to the diagnostics stream.
- Hard-broke the CLI and executor publication seams away from omnibus routers. Discovery,
  mutation, book-read, report, plan, and failure rendering now flow through narrower bounded
  writers, while bookkeeping read pages, reports, and statements project through separate
  published-language translators and matching focused tests instead of one coupled read-side
  surface.
- Hard-broke the posting-register and account-ledger CSV surfaces into normalized row-kind
  contracts. One-to-many relations such as posting accounts, ledger counterpart accounts, source
  documents, and approvals now publish as dedicated child rows instead of packed multi-value CSV
  cells, and account-ledger opening and closing totals now live only in explicit summary rows.
- Hard-broke discovery and operator help toward one clearer front door. `help --output json` and
  `capabilities --output json` now default directly to the compact discovery tier, text help now
  leads with `Start Here` / `Next Step` task guidance instead of mixing workflow and grammar
  equally, and text capabilities now separate operator overview from machine-contract retrieval.
- Hard-broke Java structural governance from convention-only discipline to mechanical repository
  gates. PMD now fails god-class, method-count, complexity, and coupling violations; repo-owned
  source-shape budgets fail oversized Java files; duplication checks reject large repeated
  translation-heavy blocks; and architecture tests prove primary module boundaries and cycle
  freedom.
- Hard-broke the protected-book adapter and local verification tooling into narrower public seams.
  Protected-book access now opens through dedicated administration, read, posting,
  period-result-transfer, plan-execution, and rekey session families, while local Jazzer operator
  commands now flow through focused replay, finding, seed-promotion, and seed-audit owners
  instead of one broad utility surface.
- Hard-broke CLI and PDF report rendering into report-family owners plus shared support for text
  tables, statement sections, balance layouts, and page theming instead of multipurpose renderer
  accumulation.

### Fixed

- Fixed the managed SQLite distribution contract to fail closed when required or forbidden compile
  option lists are missing, null, malformed, blank, duplicated, or unexpectedly empty.
- Fixed Python tool resolution so repository checks now require the exact pinned Python baseline
  instead of probing unrelated local interpreter versions from `PATH`.
- Fixed protected-book passphrase and rollback failure surfaces so key-file, prompt, and rollback
  copy failures publish only redacted public path hints or opaque source labels such as `key file`,
  `standard input`, and `interactive prompt`.
- Fixed the user and developer docs to match the current `internal-error` contract, classified
  runtime exit codes, diagnostics-stream repair flow, and the current protected-book format line.
- Fixed the source-checkout launcher, release-smoke workflow, bundle field-audit harness, and
  checked-in report examples so the verifier surface now proves wrapped help output, normalized
  CSV/report empty and comparative contracts, and the canonical non-`--output` `execute-plan`
  failure path.
- Fixed text report and export churn across the read/report family. Account-ledger, trial-balance,
  financial-position, income-statement, changes-in-equity, and period-summary surfaces now use
  result-first text ordering, compact context sections, explicit empty-state language, and
  message-bearing CSV empties with one `recordKind` envelope vocabulary.

## [0.47.0] - 2026-05-26

### Changed

- Hard-broke the protected-book format to version `21`. Canonical `YYYY-MM-DD` local dates and
  canonical UTC instants are now owned by one shared `CanonicalTemporalText` contract that drives
  CLI parsing, machine request schemas, SQLite persistence formatting, query bindings, and
  file-format checks.
- CLI distribution, source-checkout artifact, Docker-context, runtime-image, and bundle-manifest
  ownership now live in shared Gradle build logic instead of the CLI module build script. The
  generated checkout and Docker manifests now include the Gradle wrapper and build-logic inputs
  that govern the shipped artifacts.
- Normal CI now path-proves every published Unix bundle classifier before merge through the same
  repo-owned bundle-smoke contract used by release publication.
- Hard-broke the public response contract to one `ProtocolEnvelopeStatus` vocabulary
  (`ok`, `rejected`, `error`) across machine discovery, response descriptors, and JSON envelopes.
  `execute-plan` now keeps plan-family result state inside `payload.status` instead of inventing a
  command-family outer status.
- Discovery and help now hard-break into clearer public tiers. Minimal JSON discovery exposes the
  smallest machine index, compact discovery carries the stable command and request descriptors, and
  the text help front door now leads with one shortest successful path before the deeper grammar.
- Successful JSON surfaces that mention filesystem artifacts now publish redacted public-path hints
  instead of absolute operator filesystem paths.
- Text read and report surfaces now use one explicit empty-state vocabulary, preserve report
  section skeletons when filtered sections are empty, and keep account-ledger CSV summary facts in
  the summary rows instead of repeating them on every ledger-entry row.
- SQLite key-file, passphrase, and resolver ownership now lives under dedicated
  `dev.erst.fingrind.sqlite.secret` packages and tests instead of being mixed through the broader
  store/runtime package.

### Fixed

- Fixed distributed CLI artifacts so release bundles no longer embed absolute source-checkout or
  build-root paths in their JAR manifest/runtime metadata. Checkout-local path facts now remain a
  source-checkout launcher concern instead of leaking into the shipped binary surface.
- Fixed the source-checkout and direct-Java launchers so cached raw-JAR execution now refreshes by
  manifest SHA-256 proof of tracked source inputs instead of looser freshness heuristics.
- Fixed SQLite file-format drift by adding durable schema checks for canonical posting dates,
  recorded timestamps, source-document dates/capture timestamps, approval timestamps, period-close
  dates/timestamps, audit timestamps, and the closed `source_channel` vocabulary.
- Fixed machine-readable request schemas so date and timestamp fields now publish exact regex
  assertions together with `format` hints, matching the same canonical grammar the CLI enforces at
  execution time.
- Fixed the source-checkout launcher, runtime verifiers, public-container verifier,
  protected-book format contract docs, checked-in SQLite fixtures, and release-smoke/public-doc
  owners so the published contract now matches the live format-`21` storage surface, current
  discovery/help tiers, current `ProtocolEnvelopeStatus` envelope shape, current redacted-path
  policy, and current list-postings summary payloads.
- Fixed grouped maintenance path hints so backup, restore, and rollback responses now widen their
  redacted trailing path context just enough to keep sibling artifacts distinguishable instead of
  collapsing different files into the same visible hint.

## [0.46.0] - 2026-05-25

### Changed

- Discovery JSON now has an explicit three-tier contract. `help --output json` and
  `capabilities --output json` default to a minified `minimal` index for agents, while
  `--detail compact` exposes the stable command/output descriptors and `--detail full` keeps the
  exhaustive embedded schemas and doctrine surface.
- Hard-broke the operator-output vocabulary from the old audience-labeled mode to `text` across CLI parsing, machine
  discovery, help text, request docs, examples, release-smoke owners, and repo-owned renderer/test
  naming, so the public contract now names one representation instead of guessing its audience.
- `print-request-template` and `print-plan-template` now emit unmistakable placeholder-first
  scaffolds instead of live-looking demo provenance. The checked-in template corpus, quick starts,
  and request docs now publish replace-before-submit evidence and provenance fields together with
  the current `PERSON` default actor classification.
- Committed posting facts now preserve one durable `postingOriginKind` across `get-posting`,
  `list-postings`, `account-ledger`, ledger-plan journals, and the checked-in example corpus, so
  typed entry families survive durable storage and readback instead of collapsing into generic
  `postingKind` alone.
- Typed published entries now enforce their own bookkeeping semantics before journal derivation.
  Cash, equity, and administrative entry flows validate account type or classification
  compatibility plus accepted source-document types, and deterministic failures publish structured
  `entry-semantics-violations` instead of accepting any balanced journal shape that happens to post.
- Text account, posting, ledger, and statement views are now summary-first. The operator-facing
  read/report commands render compact tables instead of repeating the same facts as one
  multi-line block per row.
- `list-postings` JSON pages now return posting summaries rather than full posting bodies, so list
  reads expose stable identifiers, movement totals, source-document ids, and origin kinds without
  forcing agents to pay full posting-detail cost for every page entry.

### Fixed

- Fixed the public container verifier and tag-driven container publication checks so the mounted
  `open-book` workflow no longer emits a malformed blank argument token. The workflow-owned fake
  Docker regression harness now rejects unexpected verifier arguments instead of silently tolerating
  them, and the CLI deterministic-failure envelope now normalizes blank optional argument metadata
  to absence instead of crashing while rendering an invalid-request response.
- Fixed the Windows CI bundle lane so the repo-owned Defender exclusion owner now treats an
  unavailable Windows Defender service as a warning instead of a release-blocking workflow
  failure. The Windows product gate keeps proving the real Gradle, runtime, and bundle smoke
  surfaces, while the antivirus exclusion remains an optional performance optimization.
- Fixed posting and report CSV exports so they are now scalar-only and rectangular. Nested evidence
  metadata no longer appears as escaped JSON inside cells, `account-ledger` uses one repeated
  entry-row model instead of mixed opening/entry/closing record kinds, and the statement CSV
  outputs no longer depend on blank-heavy multiplexed row shapes.
- Fixed the last verbose period-summary fallback. Period-summary account activity now renders the
  same compact summary table shape as the other text read/report surfaces instead of collapsing
  back to one block per account.
- Fixed the interactive prompt contract across product, docs, and release verifiers. Prompt input
  now remains a `text`-only route, machine-output prompt requests are rejected deterministically as
  `invalid-request`, non-interactive text prompt failures are verified against the published
  `interactive-prompt-unavailable` contract, and the field-audit PTY harness now proves the real
  non-echo prompt behavior instead of recording terminal echo as product output.
- Fixed the release-smoke, source-checkout launcher, and public-container verification owners so
  they derive prompt, discovery, and output-shape expectations from the current published contract
  instead of pre-break verifier assumptions.
- Refreshed the operator and machine-facing docs to match the shipped discovery defaults, compact
  text report shapes, current scaffold semantics, and current example outputs.

## [0.45.0] - 2026-05-22

### Changed

- Narrowed the initialized-book identity and bookkeeping kernel to the live
  cash-oriented single-entity contract. `open-book`, `inspect-book`, request schemas, examples,
  and machine discovery now publish only entity name, business activity tags, functional
  currency, and fiscal-year anchor, and the protected-book format advances to `19`.
- Replaced the machine-discovery `accountingBaseline` and extension-shaped `policyPack` surfaces
  with a narrower `bookkeepingKernel` contract, so help, capabilities, request docs, and
  protocol references publish only the executable bookkeeping kernel instead of a broader
  standards-baseline posture.
- Replaced generic `MANUAL_ADJUSTMENT` bookkeeping-entry language with named administrative
  adjustment entry kinds (`OPEN_ACCOUNTING_POSITION`, `REVERSAL_ADJUSTMENT`, and
  `REVERSAL_ADJUSTMENT`) across the public request contract, checked-in examples, release-smoke
  fixtures, and fuzz/replay inputs.
- Neutralized equity and period-close vocabulary across the bookkeeping kernel. Legal-form
  identity and equity-classification assumptions are gone from the active public model,
  `transfer-period-result` now targets `RESULT_HOLDING`, and statements/readback use the same neutral
  equity taxonomy across Java, SQLite, CLI output, and examples.
- Renamed and rewrote the accounting ADR line around current executable truth:
  [ADR_ACCOUNTING_FOUNDATION.md](./docs/ADR_ACCOUNTING_FOUNDATION.md) replaced the old
  `ADR_10X_ACCOUNTING_FOUNDATION.md`, and
  [ADR_ACCOUNTING_KERNEL_SCOPE.md](./docs/ADR_ACCOUNTING_KERNEL_SCOPE.md) replaced the old
  `ADR_ACCOUNTING_BASELINE.md`.

### Removed

- Removed `entityForm` and `ownerModel` from initialized-book identity, `open-book` grammar,
  request schemas, examples, and machine discovery so the live public contract no longer carries
  dormant legal-form vocabulary.
- Removed the public `accountingBaseline`, `nextTarget`, `policyPack`, and extension-surface
  capability facts from discovery so unsupported broader accounting-foundation posture is no
  longer published as live machine truth.

### Fixed

- Realigned the public-container release verifier with the shipped compact text trial-balance
  surface. The operator-side anonymous pull-and-run check and its shell regression harness now
  assert the current `Book ... | Currency ... | FY ... | Policy ...` header plus the published
  current-totals block instead of an older multi-line entity banner.
- Realigned the release-smoke workflow and source-checkout launcher verifier with the narrowed
  book-identity contract and named administrative adjustment vocabulary, removing retired
  entity-form, owner-model, and generic manual-adjustment assumptions from the public verification
  surface.
- Refreshed the README, quick-start guides, protocol references, request docs, and checked-in
  example corpus so the published snippets show the live bookkeeping-kernel and administrative-adjustment surfaces instead of
  superseded intermediate contract shapes.

## [0.44.0] - 2026-05-22

### Added

- Added a typed public bookkeeping-entry write contract. `preflight-entry`, `post-entry`,
  `print-request-template`, `execute-plan`, the machine-discovery schemas, and the checked-in
  example corpus now center on stable `entryKind` event shapes (`CASH_REVENUE`, `CASH_EXPENSE`,
  `EQUITY_CONTRIBUTION`, `EQUITY_WITHDRAWAL`, and `MANUAL_ADJUSTMENT`) instead of one generic raw
  posting-request object.
- Added durable retained-evidence facts to the public bookkeeping surface. Source documents now
  carry document date, capture timestamp, storage locator, and lowercase SHA-256 digest metadata,
  while approvals now carry approver identity, approver type, explicit decision, and approval
  timestamp facts across the contract, examples, and replay/fuzz fixtures.

### Changed

- Narrowed the protected-book identity and policy model to the executable bookkeeping kernel.
  `open-book` now selects entity name, entity form, owner model, business activity tags,
  functional currency, fiscal-year anchor, and one persisted policy profile, and the
  protected-book format advances to `15` with canonical month/day fiscal-year storage plus the
  same owner-model vocabulary enforced across Java, SQLite, examples, and machine discovery.
- Posting application now translates the published typed bookkeeping-entry events through the
  selected accounting policy profile instead of exposing dormant accounting-basis behavior as a
  first-class public contract.
- Account declaration and chart hierarchy now distinguish `HEADER` versus `POSTABLE` nodes,
  require parent/child statement-classification parity, and surface the same hierarchy doctrine
  through the SQLite schema, CLI request schemas, examples, and report readers.
- Trial-balance and statement/report payloads now publish richer readback facts: trial balances
  include book-wide totals plus an explicit balanced verdict, report criteria use as-of
  language for point-in-time reads, and comparative date ranges remain first-class in the
  canonical report contracts.
- Text and machine discovery are now layered more deliberately. `help` is the operator task
  guide with copy-safe literal command grammar, explicit request-document scaffold/contract lookup
  cues, and compact text report headers, while `capabilities` remains the shared machine
  inventory for automation and release-surface verifiers.
- Refreshed the checked-in examples, quick-start guides, and storefront docs so the published
  snippets show the live typed-entry, retained-evidence, policy-profile, and compact-report
  surfaces rather than older placeholder or pre-refactor interface shapes.

### Removed

- Removed `PostingRequest` as the primary public write contract. Raw journal mechanics now remain
  only inside the explicit `MANUAL_ADJUSTMENT` bookkeeping-entry path.
- Removed `ReportingObligationStatus`, `AccountingBasis`, and the dormant
  `AccountingBasisPolicy` seam from the active book identity and bookkeeping policy model, along
  with the related public wording that implied behavior FinGrind did not execute.

### Fixed

- Fixed the published machine error surface so `responseModel.errorDescriptors` now carries one
  canonical `exitCode` per deterministic error, and aligned the release-smoke plus
  source-checkout launcher verifiers to consume those published values instead of stale private
  numbers.
- Fixed the text CLI operator surfaces so command help no longer hard-wraps invocation or option
  grammar mid-command, request-document sections label scaffold and contract lookup commands
  explicitly, and repeated report/register identity headers collapse into one stable compact
  book-context row instead of reprinting the same multi-line banner on every screen.
- Fixed the source-checkout and developer direct-Java wrappers so they now verify one generated
  source-hash manifest before executing the cached raw JAR, refresh `:cli:shadowJar` plus
  `prepareManagedSqlite` automatically when the checkout has moved ahead of that artifact, and
  keep the checked-in request/plan template captures aligned with the live typed-entry contract
  instead of silently replaying stale launcher bytes.
- Fixed the bundle and release-smoke evidence fixtures so they now emit real lowercase SHA-256
  digests for retained source-document metadata, and updated the launcher/release verifiers to
  keep long-running local checks alive with explicit progress pulses instead of timing out on
  quiet-but-healthy work.
- Fixed PDF artifact export permissions on POSIX hosts so `--pdf-out` now publishes mounted report
  files with host-readable permissions instead of preserving the private temp-file mode across the
  final move, added a portable default-filesystem fallback that verifies one host-readable artifact
  contract instead of treating platform-specific permission-mutation return values as the public
  invariant, and tightened the public container verifier plus its regression harness so mounted
  book/key/PDF workflows now run as the caller's numeric `UID:GID` and host read failures are
  reported as unreadable mounted PDF artifacts rather than being misclassified as non-PDF content
  failures.
- Replaced the aging third-party Windows MSVC developer-command GitHub Action with a repo-owned
  PowerShell bootstrap that locates `VsDevCmd.bat`, exports the full developer-command
  environment for subsequent steps, parses cleanly under PowerShell before execution, and removes
  the Node 20 deprecation warning from the CI and release publication workflows.
- Fixed the post-tag release replay seam so `workflow_dispatch` reruns now materialize
  workflow-owned publication helpers from `main` while rebuilding the immutable tag checkout,
  which keeps the repaired Windows MSVC bootstrap and release verifiers available even when the
  tagged source line predates those helper files.
- Fixed the tagged container publication replay seam so `container.yml` now materializes
  workflow-owned publication helpers from `main` before it rebuilds the immutable tag checkout,
  which keeps post-tag container-verifier and release-asset verifier repairs live during reruns
  instead of silently replaying stale tag-owned helper scripts.
- Fixed the release workflow's Windows MSVC bootstrap step to remain valid YAML after the
  helper-root replay refactor, and tightened the repo-owned MSVC regression owner so malformed
  workflow YAML is rejected before GitHub drops the release workflow's dispatch contract.

## [0.43.0] - 2026-05-20

### Added

- Added a public `10/10 Accounting Foundation` ADR and refreshed the developer/core theory-holder
  references so the repository now states the exact truth-ownership doctrine plus the hard-break
  bounded-context order from the current bookkeeping kernel toward the broader accounting
  foundation.

### Changed

- Posting requests, committed posting facts, posting-history/report payloads, and ledger-plan
  posting facts now carry first-class accounting evidence. Source-document references are required
  on caller-authored postings, approvals remain first-class optional references, the canonical
  request templates and examples include the evidence scaffold, and the protected-book schema moved
  to format `12` with dedicated `posting_source_document` and `posting_approval` child tables.
- `open-book` now requires explicit owner-model, reporting-obligation, and business-activity
  doctrine instead of leaving new books semantically under-specified, and `inspect-book` plus the
  text report headers now publish those identity facts together with one direct close-readiness
  summary instead of waiting for `transfer-period-result` rejection to surface that doctrine.
- The tagged container publication workflow now runs the same mounted-book and native-provenance
  verifier the Step 9 operator release protocol uses, so automated post-publish checks and manual
  public-surface verification enforce one shared container contract instead of two different
  depths.
- `print-request-template` and `print-plan-template` now emit runnable agent-first sample
  documents with demo evidence and provenance values instead of `replace-before-commit-*`
  placeholders, and the quick-start/example corpus now uses that same sample-first contract.
- `execute-plan` now returns one aggregate summary plus the optional full execution journal
  instead of duplicating per-step digests under both `payload.summary` and `payload.journal`, and
  command help now folds advisory notes into example guidance instead of rendering them as peer
  grammar sections.
- Text discovery and machine output are more sharply separated: `help` is the front-door task
  guide, `capabilities` is the reference/machine-contract inventory, JSON responses are emitted as
  pretty-printed documents, and long text-facing book paths are compacted so inspection and
  report surfaces stay readable on narrower terminals.

### Fixed

- Fixed the bundle and container release-smoke acceptance fixtures so their canonical sale and
  adjustment requests now include the same mandatory evidence bundle the public posting contract,
  shipped request template, and bundle-smoke verifier require.
- Fixed the public container verifier's native-provenance probe so it checks published files
  through an explicit shell entrypoint inside the container rather than routing `test -s` through
  the FinGrind CLI entrypoint and reporting a false missing-file failure.
- Fixed the source-checkout launcher verifier, checked-in examples, and replay/fuzz fixtures so
  they now exercise the same explicit open-book identity and posting-evidence contract the public
  CLI enforces, instead of asserting older optional-profile or placeholder-era request shapes.
- Fixed the Step 9 public-container verifier and its regression harness so the mounted-book smoke
  path now seeds the same explicit open-book identity doctrine and mandatory posting evidence that
  the released CLI contract requires.
- Fixed report and query CSV evidence serialization so nested source-document and approval JSON is
  emitted in a deterministic field order, and refreshed the checked-in text/report example
  fixtures to match the current expanded book-identity header contract.
- Fixed the text path-display contract on Windows so root paths normalize to the same forward-slash
  presentation rule the rest of the compact text CLI surfaces use, and aligned the focused CLI
  regression test with that cross-platform display contract.

## [0.42.0] - 2026-05-20

### Changed

- Protected-book maintenance now records successful backup, restore, and rollback recovery facts
  inside the encrypted `audit_event` stream instead of through an adjacent plaintext maintenance
  journal, `delete-rekey-rollback` now requires one explicit live-book passphrase source, and the
  maintenance workflow compensates those in-book audit facts when backup publication or rollback
  deletion fails before the external filesystem mutation completes.
- The public CLI example corpus is now replayed from live commands through one deterministic
  fixture harness, and the checked-in examples were refreshed to the normalized book-format-11,
  exit-taxonomy, and report/discovery contracts the shipped binary actually emits.
- Posting admission and period close are now carried by smaller local bookkeeping owners: posting
  acceptance composes focused validation policies behind one explicit policy-pack seam, and period
  close planning now lives in `PeriodResultTransferPlanner` while `PeriodResultTransferService` coordinates
  lifecycle/store access and durable close persistence.
- Statement reporting is now split across dedicated financial-position, income-statement, and
  changes-in-equity calculators, with `BookkeepingStatementService` reduced to a coordinator over
  the local read/report slice instead of one multi-statement doctrine sink.
- Text discovery, inspection, and report output now follow one wrapped front-door contract across
  packaged, source-checkout, developer direct-Java, and raw modular launchers: grouped command
  catalogs, stable section headings, trimmed whitespace, and narrower line widths are now the
  published text-output baseline.
- Read-only SQLite native opens now keep in-process active-connection accounting without
  publishing sibling activity markers, so diagnostic and query paths no longer create marker
  artifacts merely to inspect one protected book.
- Upgraded the shared JUnit BOM to `6.1.0` and moved the Java-26-ready JaCoCo pin forward to the
  exact 2026-05-19 snapshot artifact `0.8.15-20260519.201139-107`, with the developer docs kept on
  the same published coordinates.

### Fixed

- Release-verifier headroom now matches the live CI fan-out observed during the `0.41.0` release:
  the PR Gate and merge-handoff verifiers now wait longer by default before declaring timeout, and
  the release protocol documents the same explicit timeout override path for both pre-merge and
  post-merge verification.
- Cross-platform CLI surface checks are tighter at the owner seams: shared text-format helpers now
  emit one deterministic wrapped-line contract independent of host line endings, and published
  example-fixture canonicalization now normalizes path-bearing JSON and text fixtures through the
  same owned temporary-path rules on Windows, macOS, and Linux.
- Fixed release-surface and source-checkout launcher regressions that were asserting retired help
  headings or one exact unwrapped launcher line; verification now proves the live grouped-help
  contract the launchers actually publish.
- Fixed bundle and release-smoke verification drift around text posting registers so acceptance
  checks and checked-in workflow fixtures now follow the current card-style posting surface the
  shipped binary emits.
- The release protocol now states explicitly that the Step 1 baseline gate runs before the version
  sweep, so any bundle and Docker artifact names emitted there reflect the pre-sweep checkout
  version; the Step 2 post-sweep rerun remains the authoritative release-version proof.
- The release protocol now treats any Step 2 staged-diff blob or tree read failure as a checkout
  object-store defect that requires the release to move into a clean clone before publication can
  continue.

## [0.41.0] - 2026-05-19

### Changed

- Protected-book maintenance now runs through an explicit executor-owned maintenance model instead
  of SQLite-owned workflow glue: `backup-book`, `restore-book`, and the public rekey-rollback
  inspection, restore, and deletion workflows now verify initialized sources, rollback artifacts,
  and restored targets through one typed verification surface, emit durable encrypted maintenance
  audit facts, and keep operator-selected artifact outputs distinct from redacted maintenance
  diagnostics.
- CLI help, machine discovery, and canonical examples now describe the same command grammar more
  precisely: action-specific maintenance requirements are surfaced explicitly, operator guidance is
  separated from first-class grammar sections, and report/query outputs use the normalized versus
  redacted path vocabulary consistently across text, JSON, bundle-smoke, and Docker acceptance
  surfaces.
- The current bookkeeping kernel was narrowed to executable present-day meaning: inert
  reporting/basis identity labels, dormant tax/FX/evidence policy seams, and unused
  organization/reporting identity types were removed from the active model, while workflow
  assertions now preserve structured effective-date ranges instead of degrading them to nullable
  bounds.
- Managed-SQLite runtime and release provenance contracts are tighter: discovery now distinguishes
  publisher-authenticated bundle runtimes from source-checkout-managed local-build runtimes more
  clearly, managed-toolchain probing and compiler-flag rendering verify the release contract more
  aggressively, and the public developer/runtime references align with that stronger trust
  vocabulary.
- Root Python helper-tool verification now runs through a pinned repo-owned `uv` launcher instead
  of direct ambient-package imports: `fingrindUvVersion` now pins the launcher bootstrap, Ruff is
  pinned at `0.15.13`, SQLFluff `4.2.1` now lints the canonical SQLite schema through the
  repo-owned `gradle/sqlfluff/sqlfluff.cfg` contract, the shell verification entrypoints now
  auto-resolve a Python `3.12+` runtime through `uv` when the ambient `python3` is older, hosted
  CI now bootstraps the pinned `uv` launcher explicitly before Gradle-owned Python tool tasks run,
  and the developer references point contributors at the new bootstrap contract.
- The canonical SQLite schema renamed `book_meta.key` to `book_meta.meta_key` and tightened the
  recursive account-cycle trigger query so the SQLFluff-checked schema, generated schema
  reference, and SQLite adapter vocabulary stay aligned.

### Removed

- Removed the broad `SqliteBookSession` public seam and the old SQLite-owned backup, restore, and
  rekey-recovery service entrypoints in favor of narrower administration, read, posting,
  period-close, plan-execution, and rekey session surfaces plus the executor-owned maintenance
  boundary.

### Fixed

- Fixed maintenance-lease and recovery workflows that could previously strand follow-up operations
  behind generic runtime failures or filename-only rollback selection; recovery and restore paths
  now reject invalid maintenance artifacts deterministically and verify the resulting protected
  book before reporting success.
- Fixed report-export and diagnostics path drift so explicit artifact payloads publish normalized
  paths while maintenance and request-file failure surfaces redact local filesystem topology by
  default.
- Fixed release-surface Python runtime support so repeated shell sourcing, uv fallback resolution,
  and fresh-bundle verification remain deterministic across operator environments instead of
  depending on whichever host Python executables happen to be present.
- Fixed the Gradle-owned Python tool gate on hosted Windows so Ruff and SQLFluff no longer depend
  on a multiline `python -c` version probe: the shared uv task owner now verifies Python through
  the standard `python --version` banner and build-logic regression tests guard that
  cross-platform parser directly.
- Fixed repository-hygiene and release-publication drift around orphaned Git coordination locks:
  the hygiene verifier now rejects shared or worktree Git `.lock` files before release-sensitive
  verification begins, and the release/developer runbooks now spell out the live-owner versus
  orphaned-lock decision path instead of leaving staging failures to ad hoc operator recovery.
- Fixed another release-path hygiene blind spot around suppressed Git housekeeping: repo hygiene
  now rejects a persisted `.git/gc.log` before publication or checkout-sensitive verification, and
  the release/developer runbooks now require a successful manual `git gc` cleanup before that log
  can be cleared.

## [0.40.0] - 2026-05-18

### Added

- Added first-class closed-book maintenance workflows: `backup-book`, `restore-book`, and the
  explicit rekey-rollback inspection, restore, and deletion commands now ship as public CLI
  commands with structured JSON and text result shapes, deterministic rejection contracts, and
  matching maintenance/regression coverage.
- `inspect-book` now publishes the active migration-policy facts alongside lifecycle and format
  metadata so operators and automation can distinguish the current hard-break format line from a
  generic compatibility summary.

### Changed

- Narrowed the public bookkeeping kernel and protected-book identity back to executable ledger
  concepts: `open-book` and the protected-book schema no longer carry first-class tax-profile
  payloads, the live protected-book format advances to version `8`, and the public theory holders
  now describe tax, FX, source evidence, and richer reporting as adjacent contexts rather than
  live kernel-owned state.
- Discovery surfaces now default to JSON when stdout is redirected, inline accepted enum
  vocabularies for request-file commands, and separate operator notes from command grammar more
  clearly.
- Text inspection and maintenance output now render explicit entity and reporting rows, clearer
  restore/rekey secret continuity, and more precise transfer-period-result outcome language instead of
  compressed profile summaries or ambiguous empty sections.
- Managed SQLite runtime publication and verification now align around artifact-side trusted
  checksum sidecars across the bundle, source-checkout, Docker, and release-surface verifier
  paths.

### Removed

- Removed scaffold-only public business-event contracts from `contract.operations` and placeholder
  advanced-reporting contracts for cash flows, comprehensive income, and disclosure packs until
  those surfaces have owning executable contexts and durable storage.
- Removed leaked shared-kernel placeholder types for tax profiles, tax codes, FX measurement and
  evidence, source evidence, business-event lifecycle, inventory, and adjacent reporting
  classifications, together with the matching SQLite tax-profile codec and example payloads.

### Fixed

- Release publication now gives the public container workflow enough release-asset wait budget to
  survive slower GitHub-hosted platform bundle builders before multi-arch image publication
  begins.
- Tightened the protected-book lifecycle and theory-holder surfaces again: `backup-book`,
  `restore-book`, and the explicit rekey-rollback inspection, restore, and deletion commands now
  validate path collisions, backup-pair verification, rollback-artifact handling, and secret
  reuse deterministically across CLI, SQLite, schema, and docs.
- Raw `java -jar` discovery/help no longer leaves misleading native-access noise, successful PDF
  exports no longer emit a harmless direct-buffer warning, single-record text views keep exact
  durable identifiers, and the checked-in examples and rendered text fixtures now match the live
  request and output contracts.
- Tightened release-surface verification again so the direct-Java SQLite runtime verifier exercises
  the prepared-checkout runtime path it claims to prove, and the contract schema-key floor now
  includes the maintenance-operation payload keys introduced by the new lifecycle commands.
- Windows CI runtime verification no longer carries ad hoc inline PowerShell probes: the
  direct-Java and source-checkout SQLite runtime checks now delegate to canonical PowerShell
  verifier scripts that mirror the release-surface shell owners and their wrapper contracts.

## [0.39.0] - 2026-05-17

### Changed

- Expanded the initialized-book identity and transport contract so books now persist a first-class
  `taxProfile`, `open-book` accepts `--tax-profile-file` for registered books, and the durable
  protected-book format advances to version `7` for that wider identity payload.

### Fixed

- Hardened the remaining Windows release-gate test seams: CLI key-file and workflow tests now
  harden their temporary directories to the same owner-only contract as production secrets,
  hosted Windows launcher regression coverage now asserts runtime-specific launcher examples, the
  managed-SQLite ACL regression test now drives one deterministic ACL failure instead of a
  POSIX-only path assumption, the interactive-console prompt bridge no longer loses coverage on
  hosted Windows when the Unix-only PTY probe is unavailable, and stale-handle period-close
  coverage no longer leaks one live initialized database handle during cleanup on Windows.
- Replaced the last host-bound SQLite coverage assumptions with host-independent filesystem-fixture
  proofs: rollback-artifact scan failures and managed-library private-snapshot permission paths
  now execute through deterministic fixture seams instead of depending on POSIX-only host
  behavior, so the Windows release gate proves the same SQLite coverage contract as macOS and
  Linux.
- Fixed the Windows managed-SQLite verification snapshot hardening seam so owner-only ACLs now
  keep the copied `sqlite3.dll` executable by that owner, preventing Windows native-runtime loads
  from failing after snapshot verification.
- Split the `Windows bundle smoke` CI job's root-gate and build-logic verification into separate
  fail-fast steps, and tightened the release-surface regression so one failed Windows Gradle run
  cannot be masked by later PowerShell steps in the same job.
- Aligned the SQLite test infrastructure with the Windows owner-only filesystem contract: SQLite
  integration and key-file tests now harden their temporary book and secret directories before
  use, and ad hoc fixture key directories no longer inherit broader Windows temp-root ACLs that
  would make release-gate book initialization fail only on hosted Windows runners.
- Refined the field-tested operator and AI-agent CLI surface again: posting-ledger and statement
  views now keep full posting identities in text and PDF outputs, derived statement rows no
  longer expose internal synthetic line codes, reversal wording now names posting lineage
  directly, `rekey-book` success results now publish the replacement secret source, no-op period
  closes render explicit empty generated-posting output, and the canonical checked-in examples now
  use concrete identity/reporting/tax statuses, stable illustrative paths, and a runnable
  reversal example that exactly negates the published basic posting request.
- Suppressed the harmless Java 26 PDFBox direct-buffer unmapping warning during successful PDF
  report exports, so text, bundle, and packaged CLI runs no longer emit alarming stderr noise
  when they write valid PDF artifacts.
- Repaired raw application-JAR usability for discovery and failure handling: help output now
  publishes a truthful `java -jar` launcher example, `capabilities` reports the missing
  native-access prerequisite without triggering JVM warnings, and raw JAR command failures now
  stop cleanly before any SQLite FFM lookup begins.
- Restored full opaque identifiers in single-record text result views and mutation confirmations
  so operators and AI agents can copy posting, command, causation, and idempotency values
  without truncated detail blocks.
- Restored deterministic administration rejection ordering for missing books so
  `declare-account` rejects as `administration-book-not-initialized` before any chart or
  hierarchy validation runs.
- Aligned the source-checkout launcher regression and public quick-start guidance with the
  book-and-key directory security contract so FinGrind now consistently assumes it may create a
  missing private parent directory itself while refusing any pre-existing non-private directory.
- Replaced the reopened generic `BigDecimal` FX seam with one canonical `ExchangeRate`
  plain-decimal grammar so exchange-rate evidence now owns its exact quote normalization without
  reintroducing a shared decimal escape hatch into product Java surfaces.
- Realigned the public docs, checked-in examples, root README output excerpt, and rendered SQLite
  schema reference to the live `0.38.x` contract, including the widened book identity payload,
  narrower account-ledger CSV export, live `execute-plan` summary structure, and supported book
  format version `7`.
- Moved the published container image off the operator override path and onto the same
  bundle-managed SQLite runtime contract as the extracted archive, so container runtime probes and
  release-surface checks now report one publisher-authenticated native-library seam.
- Repaired the managed SQLite native-toolchain fingerprint owner on Windows: the build-logic
  probe now captures `cl.exe` and `link.exe` version banners using platform-correct invocation
  semantics instead of assuming Unix-style `--version` support, and the shared build-logic tests
  now guard that Windows probe contract directly so Windows bundle smoke is no longer the first
  detector for MSVC toolchain regressions.

## [0.38.0] - 2026-05-16

### Changed

- Bumped the pinned Gradle wrapper distribution to `9.5.1` while preserving the repo-owned
  wrapper launcher behavior that externalizes project cache, build-logic, JaCoCo, and ordinary
  project-build state outside the checkout by default.
- Upgraded Spotless to `8.5.1` and aligned the repository formatting floor, release-surface
  regressions, and shared build logic to the same formatter baseline.

### Fixed

- Tightened the accounting-foundation contract again: books now persist an explicit entity profile
  and accounting basis instead of only a bare entity name plus currency anchor, the public
  baseline now declares one exact current target and next target, reporting coverage and missing
  baseline capabilities are published structurally instead of only through prose exclusions, and
  the neutral single-entity policy pack now owns an explicit published inventory of accounting
  policy dimensions and extension seams rather than only one comparative-reporting hook.
- Hardened the Java and SQLite engineering boundary again: machine-contract request schemas are
  now deeply immutable instead of only top-level frozen, SQLite native passphrase copies are
  zeroized through an explicit native-secret owner, Java compile conventions no longer force
  every main compile out of date, native-access JVM permissions are now scoped to the SQLite
  foreign-function seam, and the Linux CI gate now runs the canonical `./check.sh` pipeline
  instead of restating a parallel source of truth.
- Tightened the internal execution and CLI seams again: the bookkeeping executor storage boundary
  is now split into narrower lifecycle, validation, read-model, write, and ledger-plan ports;
  workflow execution keeps typed plan and step identifiers instead of demoting them to raw
  strings; posting requests are sealed and exercised through exhaustive policy logic; CLI command
  option specs now reject duplicate or overlapping grammar declarations; and the application
  bootstrap now carries one explicit runtime-environment seam for stdin, stdout, stderr, and
  clock ownership instead of partially hardwiring process globals.
- Tightened the shared-kernel and discovery contract surface again: request-file guidance now maps
  explicit operation outcomes instead of hiding behind a nullable default branch, internal
  effective-date range usage now names unbounded versus bounded state directly, `CurrencyBalance`
  now keeps only canonical debit/credit totals while deriving net amount and balance side on
  demand, and semantic text-boundary normalization is now applied more consistently across money,
  identifiers, contract resources, and SQLite runtime/bootstrap inputs.
- Repaired the remaining build-logic and regression-floor drift: aggregated JaCoCo coverage wiring
  now stays provider-backed instead of eagerly realizing subproject tasks and files at
  configuration time, stale nested Java class outputs are pruned by exact source-owner manifests
  instead of deleting whole compile output trees, Jazzer workflow fixtures and replay coverage now
  speak the new narrow executor store ports directly, and the CI release-surface regression now
  verifies the canonical root `./check.sh` gate owner instead of a duplicated workflow-local
  stage definition.
- Reduced the remaining contract and SQLite god-seam load: machine-contract quick starts and
  request templates now come from dedicated contract-catalog owners instead of one imperative
  assembler path, and SQLite lifecycle/mutation orchestration now delegates secret buffering,
  rekey execution, and ledger-plan transaction coordination to narrower internal services with
  direct regression coverage.
- Repaired the post-tag container publication rerun seam: `container.yml` now publishes from the
  staged Docker context mirrored at `cli/build/docker-context` instead of reopening the repository
  root, and the container-workflow regression plus release-publication docs now guard that shared
  staged-context assembly boundary explicitly.
- Repaired the tag-driven release publication workflow after the repo-hygiene build-root
  externalization change: `release.yml` now uses the archive and checksum paths reported by
  `:cli:bundleCliArchive` itself instead of guessing checkout-local `cli/build/distributions/...`
  paths, the bundle-output task now publishes one machine-readable path contract for both Bash and
  PowerShell workflow owners, and the release-workflow regression and bundle-pruning proofs now
  guard that contract so post-tag reruns can republish the existing immutable tag safely.
- Repaired the post-tag release rerun seam again: `release.yml` now accepts both the current
  machine-readable bundle-path lines and the older text-labeled bundle-path lines that an
  immutable tagged source can emit during `workflow_dispatch`, and the release-publication docs
  plus the release-workflow regression now guard that mixed `main`-workflow versus tagged-source
  handoff explicitly.
- Repaired the SQLite protected-book proof floor again: committed compatibility fixtures now
  match book-format version `6` and the current schema fingerprint, direct SQLite account-row
  test fixtures now carry the same taxonomy invariants enforced by the live schema, and the
  explicit fixture-refresh task now stages refreshed artifacts into test runtime resources
  without making ordinary SQLite verification rewrite committed source fixtures.
- Repaired the remaining release-smoke and launcher-surface drift: `account-ledger` CSV
  acceptance checks now validate the current narrow row contract semantically instead of pinning
  an obsolete denormalized header shape, and the source-checkout, developer direct-Java, bundle,
  and discovery help surfaces now publish launcher examples and command hints through the active
  runtime launcher contract instead of flattening every surface to one generic token.
- Repaired the root release gate on mixed operator and CI platforms: the shared `./check.sh`
  monitor now measures log growth portably across BSD and GNU userlands instead of assuming macOS
  `stat -f`, and the release-surface regression floor now proves that portability explicitly so a
  green local release gate cannot hide a Linux CI failure.

## [0.37.0] - 2026-05-14

### Fixed

- Tightened the Docker/repository hygiene boundary again: container assembly now builds only from
  the staged Docker context emitted by `:cli:stageDockerBuildContext` instead of reopening the
  repository root, that staged context now includes the Dockerfile plus a full `source-root/`
  snapshot of every checked assembly input, the context manifest now verifies those staged files
  as well as their source fingerprint, and the Docker smoke gate plus developer docs now treat
  repository-root `docker build .` as intentionally unsupported.
- Tightened repository hygiene again at the Git storage boundary: the repo-hygiene verifier now
  proves the checkout is a real readable Git repository and fails fast on corrupt or unreadable
  object stores, its shell regression now covers a deliberately corrupt loose object, and the
  release protocol plus publication reference now distinguish the normal worktree path from the
  mandatory clean-clone fallback when shared repository metadata is damaged.
- Tightened repository hygiene at the checkout boundary: Stage 1 quality gates now verify the
  repository-root allowlist before any build runs, `./gradlew` now externalizes ordinary project
  build trees outside the checkout by default instead of leaving that behavior only to fragile
  mounted filesystems, and the new repo-hygiene cleanup tooling can prune empty root clutter,
  Finder droppings, generated caches, optional scratch state, and ignored tool/editor state
  without touching tracked source; the verifier's local-state report now also classifies each root
  and points at the exact cleanup flag that removes it.
- Tightened the remaining operator-facing read/export and PDF seams: close-sensitive account
  reads now accept an explicit `--posting-coverage` filter, account-ledger and period-summary CSV
  exports now preserve their full multi-section report meaning through `recordKind` rows, posting
  inspection surfaces now publish reversal state consistently, `execute-plan` now uses one stable
  `ok` envelope whose `payload.status` carries rejected and assertion-failed outcomes, and
  multi-page PDFs now render `n / nn` page labels while vertically centering both gray-band
  headers and body rows and suppressing zero-only opening-balance sections in dense ledgers.
- Tightened bounded account-ledger reporting again so selected date ranges now publish explicit
  zero opening and closing balances instead of omitting those buckets, while the bundle-acceptance
  verifier and CLI CSV regression coverage now prove the quoted-field row width that the public
  export contract requires.
- Tightened the remaining read/report and plan-transport seams so `account-balance` now returns
  the same report identity and PDF artifact metadata that sibling report commands already expose,
  posting inspection and ledger-style views now declare `postingKind` and posting-coverage
  semantics explicitly, `execute-plan` now defaults to bounded summary output while public docs
  and examples show `--result-detail full` wherever they rely on the full execution journal, and
  the text/PDF report layout now suppresses empty statement sections while vertically centering
  gray header titles and drawing matching top and bottom header rules.
- Repaired the operator-side public container verifier to use the current mounted-book contract:
  it now opens books with explicit identity fields and seeds postings with the required
  `postingKind`, while the paired shell regression and release-publication docs now track mounted
  lifecycle and posting-grammar changes as well as text `trial-balance` layout changes.
- Tightened the bookkeeping standards boundary and reporting-policy surface so `help` and
  `capabilities` now publish one machine-readable accounting baseline plus the sanctioned
  bookkeeping policy-pack extension seams, comparative statement data is now derived and carried
  through fiscal-year-anchored report payloads instead of date metadata alone, opening balances
  now reject deterministically after the first committed posting, and the
  request/response docs now match the live statement, PDF-export, and opening-balance contract.
- Hardened the public bookkeeping-kernel boundary again: `capabilities` and `help` now publish
  explicit small-entity and organizational-position facts, the extension surface now distinguishes
  the one live comparative-policy seam from adjacent future contexts such as tax, FX, subledgers,
  and consolidation, the accounting baseline now also states the current reporting boundary, flat
  chart model, operational-domain boundary, and non-first-class tax posture explicitly, and
  opening-balance admission is now a one-time pre-posting opening-statement window rather than a
  looser “before ordinary activity” rule.
- Tightened the packaged CLI discovery and report UX surface so command-scoped JSON help now emits
  one narrow command-local contract instead of dumping the full machine catalog, `help
  execute-plan` and `print-plan-template` now publish the same canonical ledger-plan template,
  `print-request-template` can emit the declare-account scaffold directly, JSON report success
  envelopes now publish PDF artifacts explicitly, account-ledger and period-summary now carry
  book identity, text/CSV/PDF reports now expose the comparative data and presentation labels
  already present in the model, and the checked-in public docs/examples now match that live
  transport exactly.
- Realigned the bundle and release-smoke acceptance workflow to the current account-ledger CSV
  contract, so the shared verifier now expects the public identity-prefixed ledger header and rows
  that the shipped CLI actually emits instead of the retired pre-identity CSV layout.
- Realigned the source-checkout launcher shell regression to the current discovery contract, so the
  raw developer JAR now proves the generic front-door `help` surface and the direct-Java launcher
  syntax where it actually belongs in command-scoped help instead of asserting the retired
  runtime-specific quick-start block.

## [0.36.0] - 2026-05-14

### Changed

- Bumped the included Gradle build-logic compiler pin to Kotlin `2.4.0-RC2` and moved the
  Java-26-ready JaCoCo snapshot pin forward to `0.8.15-20260513.074320-106`, with the matching
  developer-build references updated to the same live coordinates.
- Hardened the current bookkeeping foundation around explicit book identity and reporting scope:
  `open-book` now requires entity name, functional currency, and fiscal-year start, the same
  identity is now returned by `open-book` and `inspect-book`, the canonical AI-agent
  `print-plan-template` scaffold now carries the nested `openBook` shape explicitly, and the
  command docs, quick-start guides, examples, and bundle README now match that live contract.
- Tightened the current reporting and provenance model for transfer-period-result and statement surfaces:
  period-close now records system-generated provenance with a non-CLI source channel, future-dated
  closes reject explicitly, close ranges may not cross the configured fiscal-year boundary,
  trial-balance/report contracts now expose whether closing entries are included, every statement
  report now carries book identity plus one comparative effective-date range, and the durable
  protected-book format is promoted to version `4` for the expanded lifecycle, identity, and
  opening-balance model.
- Promoted the caller-authored posting surface from one implicit journal family to one explicit
  doctrinal choice: direct posting requests and AI plan templates now declare `postingKind`,
  FinGrind accepts `STANDARD` and `OPENING_BALANCE` for caller-authored requests, opening-balance
  postings may seed only asset, liability, or equity accounts, every posting must match the
  selected book functional currency, and transfer-period-result now targets one explicit retained-earnings
  account instead of relying on a singleton retained-earnings bucket in the schema.
- Declared FinGrind's current accounting-standards baseline explicitly: the repo now names a
  country-agnostic bookkeeping-core target informed by the IFRS conceptual layer and functional
  currency doctrine, while also stating that full cash-flow, OCI, note/disclosure, and
  multi-currency translation layers remain intentionally out of scope for the current core line.

### Fixed

- Tightened the root README opening prose so the front page now explains FinGrind in direct,
  concrete bookkeeping language instead of relying on metaphor-heavy wording.
- Tightened the packaged CLI transport contract so every successful JSON command now uses one
  top-level `status: "ok"` envelope instead of mixing `ok`, "preflight-accepted", "committed",
  and "plan-committed", while the operation-specific meaning now lives consistently inside the
  payload. The same cleanup also corrected the published request/response examples, account-ledger
  CSV example, release-smoke verifiers, and operator docs to the live hard-break contract.
- Repaired the operator-side public container verifier so release publication now checks the
  current text `trial-balance` surface, including the first-class `Account type` and
  `Account role` columns, and aligned the mock-backed shell regression harness to the same
  mounted-book statement contract.
- Corrected contra nominal-account arithmetic so contra revenue and contra expense balances now
  offset profit and loss in the right direction, period-close generated entries remain balanced
  when contra nominal accounts are present, financial-position current-period-result projection now
  respects the same accounting doctrine, and the direct doctrine tests now assert the corrected
  sign rules instead of teaching the inverted behavior.
- Realigned the Jazzer operator and replay proof floor to the current hard-break bookkeeping
  contract: committed request fixtures now include the explicit `postingKind` field, the shared
  replay helpers open books with matching functional currency, the round-trip/workflow coverage
  harnesses speak the current `open-book` and trial-balance/report grammar, and the seed-audit
  / replay tool tests now verify the live operator surface instead of retired request shapes;
  the deterministic replay fixtures now use the required nested `openBook` payload everywhere,
  the SQLite and replay lifecycle status mappings now share one rejection-to-status owner, and
  the full Jazzer wrapper gate is back in sync with the current bookkeeping contract.
- Brought the release and bundle acceptance workflow onto the same explicit book-identity contract
  as the shipped CLI: the shared release-smoke runner now passes `--entity-name`,
  `--functional-currency`, and `--fiscal-year-start` to `open-book`, and it verifies that
  initialized books echo the same identity back in the public JSON response.
- Updated the SQLite schema-document regression harness to track the live protected-book format:
  the renderer regression now asserts the current canonical `user_version = 4` body instead of
  the retired version-2 snapshot, so release-surface verification checks the real schema line
  rather than failing on an obsolete alpha-era expectation.
- Hardened the Stage 5 release-surface proof workflow itself: the canonical release-surface
  runner now announces each subcheck before it starts, and the long Jazzer replay/seed wrapper
  regressions now emit deterministic progress checkpoints so healthy verification runs do not get
  killed as false stalls by the root-gate watchdog.
- Brought the source-checkout launcher regression onto the live initialization contract: the
  launcher and direct-Java smoke path now open books with explicit entity name, functional
  currency, and fiscal-year start, and the regression proves that both launcher surfaces echo the
  same `bookIdentity` back instead of only checking for a generic ok status. The same verifier now
  requests text `help` output explicitly when it is asserting text guidance, so the regression
  matches the shipped interactive-vs-redirected stdout contract instead of relying on a stale
  pre-hard-break default.

## [0.35.0] - 2026-05-13

### Changed

- Promoted account doctrine into the current public and durable bookkeeping model:
  `declare-account` and declared-account/report payloads now carry first-class `accountType` plus
  immutable `accountRole`, period close and retained-earnings handling are now explicit public
  operations rather than implicit future theory, FinGrind now publishes an explicit flat-chart and
  opaque account-code policy instead of leaving chart semantics implicit, and the public contract
  package is now split into semantic bookkeeping, workflow, discovery, and runtime subpackages
  rather than one flat DTO namespace.
- Promoted the protected SQLite book format to version `2` and hardened the current alpha storage
  line around the intended model directly: the account registry now persists `account_type`, the
  durable book now carries an append-only `audit_event` stream plus immutable-row triggers for
  committed posting, journal, and audit rows, and current FinGrind rejects older book formats
  instead of carrying migration code or compatibility shims.
- Added explicit aggregate and storage decision references for the current model: the docs now
  publish named consistency boundaries for lifecycle, account registry, posting ledger, reversal,
  idempotency, workflow transaction, and audit stream ownership, and they now publish the durable
  rationale for pinning SQLite `journal_mode=DELETE` on the current storage line.
- Added first-class financial-statement surfaces to the current bookkeeping model: the query/report
  contract now includes financial position, income statement, and changes in equity outputs, with
  the CLI, report rendering, discovery contract, and documentation aligned to the same named
  accounting surfaces.

### Fixed

- Moved exact balance arithmetic out of the SQLite adapter and into the shared accounting kernel,
  added direct fault-injection and bypass-corruption coverage for SQLite commit atomicity and
  book-open integrity, and tightened the public/user/developer references so request scaffolds,
  report shapes, format-version guidance, and schema references all match the implemented model.
- Tightened the accounting proof floor around the new statement and transfer-period-result surfaces:
  multi-currency statement ordering, loss-side current-period-result projection, undeclared
  profit-and-loss bypass resilience, period-close currency bucketing, and audit-event payload
  validation are now covered directly, while the shared JaCoCo XML verifier now reads only
  report-root coverage counters and the remaining dead close-policy/audit-validation branch
  artifacts were removed from the implementation.
- Fixed the operation-id discovery contract drift for `transfer-period-result`, `financial-position`,
  `income-statement`, and `changes-in-equity` so bundle verification, release-surface scripts,
  and other machine readers now load the same canonical semantic mapping that the published
  protocol enum and CLI discovery catalog expose.
- Updated the shared release-smoke and bundle/container acceptance expectations for the
  first-class `accountRole` column now emitted by `account-ledger --output csv`, so the public
  acceptance floor matches the current exported report surface instead of the retired
  pre-doctrine header shape.
- Tightened the packaged CLI operator surface around request repair, transfer-period-result guidance, and
  statement presentation: invalid account doctrine now rejects as `invalid-request` instead of
  `runtime-failure`, command-scoped help for request-file commands now inlines canonical templates
  plus accepted fields and enums, text rejections now surface repair hints and typed details,
  successful `transfer-period-result` output now reports the retained-earnings account and closed totals,
  the first `transfer-period-result` now accepts leading empty days before the earliest posting while later
  closes remain strictly contiguous,
  text financial statements now render named sections and totals instead of raw transport tokens,
  `print-request-template` now rejects stray flags precisely, and successful `rekey-book`
  verification no longer warns about its own transient rollback copy.
- Cleaned up the PDF statement surfaces so the packaged financial-position, income-statement, and
  changes-in-equity exports now use readable black text plus corrected vertical spacing that keeps
  section rules and table borders from cutting through headings or row text.

## [0.34.0] - 2026-05-10

### Changed

- Replaced the public decimal-string money seam with one exact-money model across core,
  contracts, CLI, workflow facts, reporting, and PDF rendering: `CurrencyUnit` now owns
  ISO-backed currency semantics and minor-unit scale from FinGrind's pinned registry snapshot,
  `Money` and `PositiveMoney` now store exact minor units instead of `BigDecimal`, public request
  and response payloads now use typed money objects with `currencyCode` and `minorUnits`, and
  journal-entry/report rendering now projects one shared canonical money model instead of mixing
  free-form decimal text with formatter-local fallback rules.
- Promoted the protected-book format to schema version 2 and broke durable journal-line storage
  from free-form decimal text to exact `amount_minor` plus `currency_code`, while SQLite
  open-time verification now proves the schema fingerprint,
  `integrity_check`, `foreign_key_check`, persisted money integrity, and durable double-entry
  balance instead of trusting only table presence and initialization markers.
- Added explicit exact-money transport bounds at the machine and CLI edge: `minorUnits` is capped
  at the 19-digit signed-64-bit non-negative range, and every request JSON document is capped at
  `1048576` UTF-8 bytes whether it is read from a file or standard input.
- Expanded the exact-money regression floor across zero-digit, two-digit, and three-digit currency
  scale buckets: committed Jazzer replay seeds now cover JPY and BHD request parsing, posting
  workflow, ledger-plan assertion execution, and SQLite round-trip durability, while focused core,
  CLI, PDF, and SQLite tests now prove exact parse/persist/render behavior across those same
  currency-scale families.
- Added a dedicated decimal-boundary reference and a repository guardrail that keeps product Java
  surfaces free of generic `BigDecimal` seams, so future tax rates, percentages, exchange rates,
  discounts, and allocation ratios must arrive as their own exact domain types instead of
  reusing the posted-money model.
- Hardened the shared Java coverage gate so each `Test` task now starts from a fresh JaCoCo
  execution-data file and module verification now fails on any missed line or branch reported in
  `jacocoTestReport.xml`, eliminating false negative drift between stale `.exec` files,
  generated reports, and the named coverage-verification task under the Java 26 toolchain.
- Promoted the repo-owned Python helper scripts into the canonical root verification surface:
  `check` now runs Ruff lint plus format checks over `scripts/**/*.py` through the shared root
  Gradle conventions, CI now pins Python explicitly with `actions/setup-python`, the contributor
  devcontainer now includes `python3 -m pip`, and the repo now ships pinned Ruff configuration and
  tool-manifest files instead of relying on ambient runner tooling.

### Fixed

- Removed the remaining dead string-money seams from committed Jazzer request corpora and replay
  metadata, regenerated the committed deterministic replay floor from the typed money contract, and
  aligned exponent-invalid replay assertions with the new authoritative `minorUnits` rejection
  boundary instead of the retired free-form `amount` parser message.
- Updated the shared release-smoke fixture generator, bundle acceptance workflow, and public
  container surface verifier to submit typed money request bodies with nested `amount`
  objects instead of the retired line-level `currencyCode` plus decimal-string `amount` shape,
  so shipped bundle and container acceptance now exercise the same exact-money contract that the
  CLI, workflow engine, and published examples describe.
- Replaced the hand-maintained SQLite schema reference with one generated document derived from the
  canonical `book_schema.sql`, so schema checks, durable money columns, version markers, indexes,
  and integrity posture cannot drift between the source schema and the published reference.
- Rewrote the remaining operator and machine-contract wording that implied the retired decimal
  money seam, so CLI help and contract schema descriptions now describe typed exact-money objects
  and ASCII-digit `minorUnits` instead of vague decimal-string amounts.

## [0.33.0] - 2026-05-08

### Added

- Added `docs/DEVELOPER_RELEASE_PUBLICATION.md` as the maintainer reference for GitHub Release
  publication topology, published-byte attestation rules, Windows ZIP canary behavior, neutral
  `gh release download` job constraints, and the safe `workflow_dispatch` repair path for
  workflow-only post-tag publication defects.
- Added project-owned Jazzer seed operators for promoting ad hoc replay inputs into committed
  regression seeds and for auditing the committed seed floor, including required coverage-intent
  metadata, duplicate-content detection, orphaned-input detection, and rejection of committed
  `unexpected-failure` expectations.

### Changed

- Hardened `jazzer/bin/seed-audit` into a full committed-corpus integrity check so it now reports
  unreadable metadata, escaped or missing input references, non-file inputs, and malformed
  committed `.json` seed bodies as first-class audit defects instead of surfacing them only
  through indirect failures.
- Tightened the Jazzer custom-seed operator surface so wrapper-side `--json` failures are
  machine-readable before Gradle starts, `promote-seed` now enforces corpus-wide
  `coverageIntent` uniqueness, and the seed-management help text prints the supported replayable
  target keys directly.
- Upgraded the managed SQLite baseline from SQLite3 Multiple Ciphers 2.3.3 / SQLite 3.53.0 to
  SQLite3 Multiple Ciphers 2.3.4 / SQLite 3.53.1 across the vendored amalgamation, managed-runtime
  contract metadata, Docker/build surfaces, nested Jazzer build, developer references, and
  operator-facing CLI/runtime documentation.
- Pinned JaCoCo to the newer Java-26-ready snapshot artifact `0.8.15-20260506.113836-98` and
  updated the developer build references to match the exact immutable coordinate resolved from the
  Sonatype Maven snapshots repository.
- Split executor and SQLite lifecycle inspection, query rejection, posting rejection, and
  workflow-fact models away from the public contract so the local seams now translate to
  `BookInspection`, `BookQueryRejection`, `PostingRejection`, and `LedgerFact` only at exported
  application-service or published-language boundaries.
- Pulled bookkeeping read semantics into `BookkeepingReadService`, bookkeeping posting semantics
  into `BookkeepingPostingService`, and workflow execution semantics into
  `BookWorkflowExecutionService`, while shrinking `BookReadService`, `PostingApplicationService`,
  and `LedgerPlanService` into published-language adapters and removing the fixture-only
  `commit(CommittedPosting)` production seam from `BookStore` and SQLite.
- Forced full main-source recompilation after stale-class pruning in both the shared Java build
  conventions and the nested Jazzer build so grouped top-level command classes are regenerated
  into emptied output directories instead of disappearing behind incremental compile drift.
- Pruned nested Jazzer processed-resource destinations before each real resource sync so renamed
  or deleted committed seeds cannot linger in cached `jazzer-build/resources/` outputs and skew
  packaged corpus behavior away from `src/fuzz/resources`.

### Fixed

- Split command-scoped help so executable examples and operator notes no longer share one raw
  `Examples` section, changed invalid invocation failures to default to text repair text
  unless a recognized machine output mode is selected explicitly, and aligned text
  deterministic contract-failure rendering on the `Rejected` heading.
- Hardened container-image assembly so `docker build` now verifies the staged
  `cli/build/docker-context/` payload against a SHA3-256 fingerprint of the current CLI,
  contract, core, executor, report-PDF, SQLite, and Gradle build inputs, which turns stale
  staged Docker contexts into loud build failures instead of silently packaging an older
  application jar or Docker entrypoint.
- Removed the source book's absolute filesystem path from rendered PDF report content and PDF
  metadata, tightened the protocol-owned public-distribution and managed-SQLite contract loaders
  so required canonical array keys cannot disappear into silent empty defaults, and replaced the
  last boolean book-initialization shortcuts in SQLite tests and helpers with the inspection-first
  lifecycle seam introduced by the local bookkeeping/workflow boundary refactor.
- Tightened the managed SQLite compile contract so the canonical protocol resource now owns the
  required compile options, forbidden compile options, and SQLite3MC secure-memory requirement in
  one place, while runtime discovery, bundle metadata, build logic, and shell verifiers all prove
  the same contract instead of mixing one required-subset check with separate handwritten flags.
- Fixed public-release provenance so `.github/workflows/release.yml` now attests the exact bundle
  and checksum bytes downloaded back from the published GitHub Release on one neutral post-upload
  job instead of attesting per-runner local artifacts, which closes the Windows publication drift
  where repository attestations could point at different digests than the shipped release assets.
- Fixed the neutral published-asset attestation job so `gh release download` now receives the
  repository explicitly, retries with `--clobber`, and prints the final GitHub CLI error on
  failure instead of looping through opaque download failures.
- Raised the release verifier job timeout to fit its explicit GitHub-release propagation retry
  budget, and documented that timeout/retry alignment as part of the release protocol contract.
- Clarified the public release protocol's worktree/bootstrap handoff for cases where a live
  FinGrind verification owner already holds the repo-wide verification lock, so release operators
  are told to wait or bootstrap into a clean worktree instead of deleting a live lock or starting
  competing verification in the same checkout.
- Hardened the tag-publication release workflow so published-asset attestation now runs on one
  dedicated neutral post-upload job with the exact OIDC, attestation, and artifact-metadata
  permissions required by `actions/attest`, and clarified the release protocol's recovery path
  for workflow-only tag-publication defects: fix `main` and rerun `release.yml` or
  `container.yml` with `workflow_dispatch` against the existing release tag instead of moving or
  duplicating the tag.
- Fixed release-rerun publication convergence so `publish-github-release.sh` now replaces
  same-named GitHub Release assets when their digest differs, the release workflow's own
  verification step now carries the same release-asset propagation retry budget as the container
  workflow, and `verify-github-release.sh` now reports the exact failing sub-check and asset name
  instead of collapsing every publication defect into one generic "missing or incomplete" error.
- Narrowed test-only null escape hatches from class-wide and method-wide opt-outs to exact
  typed-null call sites, tightened CLI/contract null diagnostics and payload typing, and replaced
  the SQLite store lifecycle's nullable field mesh with an explicit session-state model so the
  compiler, tests, and runtime contracts now describe the same state transitions.
- Replaced duplicate committed Jazzer seed bytes across posting-workflow and SQLite harnesses with
  harness-specific seeds, fixed the `jazzer/bin/seed-audit` zero-target shell path under
  `set -u`, and updated the committed seed inventory/docs to reflect the stricter seed-management
  contract.
- Clarified several committed Jazzer seed coverage-intent labels so `jazzer/bin/seed-audit`
  now names exact rejected fields and persistence outcomes instead of relying on internal shorthand.
- Tightened the custom Jazzer seed operator surface so `promote-seed` now validates lower_snake_case
  seed names before Gradle launch, deterministic `--json` seed-management failures return one
  structured error payload without Gradle failure boilerplate, `jazzer/bin/regression` rejects
  stray positional arguments at the wrapper edge, and the committed `.json` regression inputs are
  syntax-checked by the deterministic Jazzer test floor after removing one corrupted
  posting-workflow seed body.
- Replaced the remaining null-to-empty constructor and helper normalization paths across core,
  contract, CLI, executor, and SQLite-support models with direct field-named null rejection,
  added explicit nullable JSON/resource seam helpers at the Jackson boundaries, and tightened the
  staged-launcher and contract-resource tests so empty or malformed inputs fail through the
  intended diagnostics instead of generic null failures while JaCoCo verification now measures
  compiled Java classes rather than treating resource outputs as uncovered code.
- Extended the stale-classfile pruning rule from the nested Jazzer build to every product-module
  `compileJava` run, so removed nested helper classes cannot linger in cached main output
  directories and reappear later as false branch-coverage failures.
- Removed the stale claim that `RejectionNarrative` owns ledger-plan failure facts now that
  workflow execution records build their local `BookWorkflowFailure` and `BookWorkflowFact`
  payloads inside the workflow context and only project public rejection prose at the outer edge.
- Updated the SQLite runtime verifier, release-smoke assertions, and bundle acceptance workflow
  to read the current `environment.sqlite.runtime` capabilities shape instead of the older flat
  runtime fields, and tightened the SQLite lifecycle coverage tests so the end-to-end gate proves
  the real deferred, created-artifact, and no-active transaction branches without reflective
  state mutation.

## [0.32.0] - 2026-05-06

### Fixed

- Hardened protected-book verification so the public `protected-book-verification-failed` contract now covers the SQLite verification families surfaced as `SQLITE_NOTADB`, `SQLITE_IOERR_BADKEY`, and `SQLITE_IOERR_CODEC` instead of letting some wrong-key or damaged-book cases escape as generic runtime crashes.
- Enforced `memory_security=fill` on every opened SQLite handle, enabled secure SQLite3MC memory support in the managed native build and Docker compiler-flag renderer, and tightened the managed-runtime identity contract so publisher-owned bundle and source-checkout runtimes are authenticated against both an embedded trusted digest and the extracted sibling `.sha256` sidecar before native symbol lookup while custom environment-configured direct-Java paths remain explicitly operator-managed.
- Fixed source-checkout managed-runtime discovery for relocated Gradle build roots by carrying the active root-project build directory through the generated launcher, developer raw-JAR wrapper, and JAR manifest instead of guessing at `repo/build/managed-sqlite`.
- Rejected missing key-file paths in the key-file security seam, capped both key-file and `--book-passphrase-stdin` passphrase payloads at 4096 bytes, hardened Windows key-file parent directories to owner-only ACLs, converted unreadable stdin failures into deterministic `invalid-book-passphrase-source` errors, and rewrote the public quick-start/help/examples to keep encrypted books under `./books/` and secrets under `./secrets/`.
- Added a public `SECURITY.md`, enabled GitHub private vulnerability reporting for the repository, and updated the security-model reference to describe the real session-scoped passphrase lifetime, checksum-backed runtime identity, attested release assets, and coordinated disclosure path.
- Added GitHub artifact attestations for every published CLI archive and checksum in `release.yml`, and tightened `verify-github-release.sh` so release verification now downloads the published assets and proves their provenance with `gh attestation verify` before treating the release handoff as complete.
- Field-tested the bundle, source-checkout, raw-JAR, and container launcher surfaces so command help now rewrites piped stdin examples to the active launcher instead of leaving `| fingrind ...` fragments behind, container launcher guidance now keeps stdin open with `docker run -i`, and successful `--pdf-out` exports now report the normalized artifact path on the diagnostics stream without changing the primary stdout contract.
- Exposed the managed SQLite runtime trust class as machine-readable `runtimeTrustBasis`, hardened key-file acceptance to require owner-only parent directories as well as owner-only files, enforced the same 4096-byte UTF-8 passphrase cap on interactive prompts as on key files and stdin, and tightened the security-reference gate so it derives trust and secret-handling facts from the live machine contract instead of only checking for documentation keywords.
- Moved the canonical paging and ledger-plan limits into shared-kernel `InteractionLimits`, replaced bookkeeping-owned public rejection imports with local rejection types plus boundary translation, and tightened managed SQLite loading so FinGrind authenticates and loads one private verified runtime snapshot instead of hashing one path and mapping another later.
- Added `./scripts/verify-security-policy-surface.sh` to verify GitHub private vulnerability reporting as part of public release verification, and updated the security reference to point at that executable evidence owner.
- Stopped read-oriented SQLite opens from rewriting book-file permissions as a side effect, added stale `*.rekey-rollback-*.sqlite` warning detection for interrupted rekeys, and clarified the security/docs contract so passphrase buffer overwrite is described as best-effort under the Java heap model rather than as guaranteed erasure.
- Tightened the public release protocol so release promotion now waits on the aggregate `Gate` check via `./scripts/verify-release-pr-gate.sh` instead of inferring merge-readiness from an earlier green `Check` job while downstream Windows and Docker fan-out is still running.
- Fixed the shared release-smoke workflow so bundle and Docker acceptance now compare the `pdf-exported` diagnostics path by normalized artifact identity instead of raw `--pdf-out` argument text, removing a Windows-only false failure when the CLI reports the canonical normalized path form.

## [0.31.0] - 2026-05-05

### Fixed

- Pinned `container.yml` runners to `ubuntu-24.04` (both the `container` and `cleanup` jobs used the floating `ubuntu-latest` label — the most security-sensitive workflow was the least pinned).
- Raised `container` job `timeout-minutes` from 35 to 45 to provide a clear margin between multi-arch image build time and the post-push verification step; the former 35-minute ceiling was tight enough that slow runners could cancel verification after a successful push.
- Added OCI build provenance (`provenance: mode=max`) and SBOM (`sbom: true`) attestations to the `docker/build-push-action` step; both are stored as OCI attestations attached to the published GHCR image digest, enabling supply-chain verification via `docker buildx imagetools inspect`.
- Added `id-token: write` permission to the `container` job to allow the OIDC token flow required for keyless provenance attestation signing.
- Pinned `gradle-wrapper-validation.yml` runner to `ubuntu-24.04`; it was the only remaining workflow using the floating `ubuntu-latest` label.
- Hardcoded the release-blocking check list in `verify-release-candidate-tag.sh` to `Gate` (the single aggregate CI check) and removed the `FINGRIND_RELEASE_BLOCKING_CHECKS` env-var override; the previous default included `Contributor devcontainer` which is legitimately skipped on commits that do not touch devcontainer files, causing the script to false-fail on any such release commit.
- Updated the branch protection reference in `RELEASE_PROTOCOL.md` §Step 1 to reflect the current single required status check (`Gate`) instead of the former three-check list (`Check`, `Windows bundle smoke`, `Docker smoke`).
- Tightened `RELEASE_PROTOCOL.md` so release hygiene now also closes any ordinary open PR that was superseded by the shipped release branch, instead of only triaging Dependabot leftovers.
- Raised `verify-github-release.sh` default retry count from 1 to 3 and default inter-retry delay from 0 to 5 seconds so release asset availability checks are resilient to brief GitHub API propagation lag when run outside the container workflow's explicit override values.
- Removed `isPreserveFileTimestamps = false` from the `bundleCliZip` and `bundleCliTarGz` archive tasks; the setting zeroed every file's modification time to the MS-DOS epoch minimum (1980-02-01 for ZIP) or the Unix epoch (1970-01-02 for TAR), making all files in every release package appear frozen in 1970 or 1980 in file managers and `ls -l` output. Retaining `isReproducibleFileOrder = true` keeps entries in a stable alphabetical order for auditing without clobbering timestamps.
- Pinned release workflow runners to `ubuntu-24.04`, `ubuntu-24.04-arm`, `macos-15`, and `windows-2022` instead of the floating `ubuntu-latest`, `macos-latest`, and `windows-latest` labels so runner image updates cannot silently change the native build environment across releases.
- Path-gated the `devcontainer` CI job so it fires only when devcontainer-relevant files actually change (`.devcontainer/`, `scripts/validate-devcontainer.sh`, `scripts/devcontainer-prepare-user-home.sh`, `scripts/repo-verification-lock-support.sh`, `scripts/python-runtime-support.sh`); non-devcontainer PRs skip the full Docker build-and-validate cycle, reducing typical PR wall-clock time by 15-20 minutes.
- Added a `devcontainer-changes` detection job that computes a git diff of the PR's changed files against the devcontainer trigger paths before the gate is evaluated. The `devcontainer` job no longer depends on `check` — the contributor environment is orthogonal to code correctness and should be proven whenever its files change regardless of whether the application gate passes.
- Added a `gate` aggregate required-status job using `if: always()` with explicit `${{ toJSON(needs.*.result) }}` failure detection so a correctly skipped `devcontainer` gate does not prevent `Gate` from being reported or block merge — only a failed or cancelled job prevents success. Configure branch protection to require `Gate` as the single required check.
- Added `workflow_dispatch:` to the CI trigger so maintainers can manually rerun the aggregate `Gate` against a branch when GitHub fails to attach the `pull_request` workflow on initial PR open.
- Pinned CI runners to `ubuntu-24.04` and `windows-2022` instead of the floating `ubuntu-latest` and `windows-latest` labels so runner image updates cannot silently change the build environment between runs.
- Added Windows Defender exclusions for the workspace and Gradle user home in `windows-bundle-smoke` before any Gradle operations begin, eliminating antivirus scan overhead that otherwise scans every `.class`, native library, and JAR file written during compilation.
- Promoted top-level `permissions: contents: read` to the workflow level and removed the redundant per-job declarations.
- Raised `check` job `timeout-minutes` from 15 to 40 to accommodate the Docker build inside the release-surface scripts verification step on days when apt mirrors respond slowly; the step consistently completes in under 5 minutes on fast days but has been observed to take over 23 minutes when mirrors are degraded.
- Unified the release and branch-protection check contract on the single `Gate` status across the release verifiers, bootstrap protocol, release protocol, and shell regressions so the path-gated contributor-devcontainer job can skip without false-failing post-merge or tag verification.
- Moved script-managed `GRADLE_USER_HOME` defaults for `./check.sh` and `./scripts/docker-smoke.sh` out of the checkout and into the same repo-keyed user-cache root used by the wrapper support helpers, restoring the documented mounted-checkout verification path.
- Fixed `cleanBundleOutputs` so `:cli:bundleCliArchive` removes obsolete `fingrind-*` bundle artifacts from the active distribution directory and legacy in-checkout leftovers before writing the current host bundle artifact.
- Replaced hardcoded `cli/build/...` local launcher commands with `scripts/source-checkout-cli.*` and `scripts/direct-java-cli.*` wrappers that resolve the active Gradle build directory, so CLI help, docs, and developer commands remain truthful when wrapper-owned build output is relocated out of the checkout.
- Restored the dedicated nested Jazzer build output root so the shared Java conventions no longer override it on relocated-checkout runs and stale-class pruning targets the real Jazzer compile output.
- Replaced the misleading former book-authentication-failed public error with `protected-book-verification-failed`, which truthfully covers wrong secrets, damaged or truncated protected books, and unsupported protected SQLite variants without pretending every verification failure is a passphrase mistake.
- Fixed atomic SQLite ledger-plan rollback for newly created books so assertion failures and other rejected plans remove the transient protected-book file and any empty parent directories they created instead of leaving a blank SQLite shell behind.
- Removed fragile qualified JPMS exports from the `executor` module, taught the Java source-policy gate to reject future `exports ... to` seams in repository modules, and hardened the Jazzer stale-class regression so nested compile runs fail on module-target warnings instead of printing them as benign noise.
- Split SQLite runtime verification by real provenance path so source-checkout launcher verification and environment-configured Gradle JavaExec verification are checked independently instead of one script claiming both.
- Replaced the ad-hoc Docker assembly inputs with one staged `:cli:stageDockerBuildContext` directory plus `docker-build-context-manifest.json`, updated Docker smoke and CI/container workflows to consume that single staged context, and kept Docker's SQLite compiler flags derived from the canonical managed-SQLite contract through `scripts/render-managed-sqlite-compiler-flags.py`.
- Added `DEVELOPER_SECURITY.md` plus a contract gate for the security model, consolidating the protected-book threat boundary, secret transport rules, runtime provenance model, and verification-failure semantics into one canonical theory surface.
- Upgraded `tools.jackson.core:jackson-databind` from `3.1.2` to `3.1.3`.
- Aligned every AFAD-managed documentation page with the current project version from `gradle.properties` and added a contract-lint gate so documentation frontmatter cannot drift onto a future or mixed release version.
- Replaced release-numbered extracted-bundle launcher paths in the public CLI guides with archive-derived launcher examples, and moved shared bundle-archive verification onto one Python owner used by both Bash and PowerShell bundle smoke.
- Taught `:cli:bundleCliArchive` to report the exact archive path and checksum path it emitted under the active Gradle build directory, and added a regression check so relocated build roots do not force operators or agents to hunt for the produced bundle artifact manually.
- Split the internal bookkeeping and workflow models away from the public contract DTOs, moved shared `CurrencyBalance` and `EffectiveDateRange` ownership into the `core` shared kernel, made `accounting entity` the canonical book-owner term across help/docs/contract facts, added a dedicated domain-model reference and gate, and moved account declaration/reactivation rules into the bookkeeping model instead of adapter-local reimplementations.

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
- `jazzer/bin/clean-local-findings` and `jazzer/bin/clean-local-corpus` now traverse local run
  state without descending into preserved corpus subtrees, and they downgrade undeletable corpus
  remnants to explicit warnings instead of aborting the cleanup command. The root `spotless`
  project-file sweep now also excludes ignored `.local/` runtime state so one unreadable local
  corpus cannot poison `./check.sh`.
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
  report argument parsers, dedicated query/report text and CSV renderers, and a top-level
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
  and `period-summary`, taught the CLI read/report surface to render canonical `json`, `text`,
  and `csv` output modes from the same report models, and added explicit `--pdf-out` export for
  report artifacts through the new report PDF adapter module backed by Apache PDFBox.
- Extended the public CLI output contract so administration and write commands that already carried
  machine envelopes can now also render operator-facing `--output text`, and deterministic
  failures on those commands now stay in the selected text format instead of falling back to JSON.
- Hardened the public verification surface so the bundle, Windows bundle, Docker image, and root
  `./check.sh` flow now run office-worker acceptance workflows instead of only narrow posting smoke
  checks.

### Fixed
- Reclassified deterministic operator-repairable failures onto contract-owned CLI error codes, so
  malformed posting cursors, wrong book passphrases, prompt-unavailable paths, key-file overwrite
  refusals, and invalid key-file contract violations now exit `2` instead of surfacing as generic
  `runtime-failure`.
- Stopped wrong-passphrase failures from leaking raw SQLite storage symptoms such as
  `SQLITE_NOTADB`; the public surface now returns `protected-book-verification-failed` with repair
  hints.
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

[Unreleased]: https://github.com/resoltico/FinGrind/compare/v0.51.0...HEAD
[0.51.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.51.0
[0.50.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.50.0
[0.49.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.49.0
[0.48.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.48.0
[0.47.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.47.0
[0.46.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.46.0
[0.45.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.45.0
[0.44.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.44.0
[0.43.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.43.0
[0.42.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.42.0
[0.41.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.41.0
[0.40.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.40.0
[0.39.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.39.0
[0.38.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.38.0
[0.37.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.37.0
[0.36.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.36.0
[0.35.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.35.0
[0.34.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.34.0
[0.33.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.33.0
[0.32.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.32.0
[0.31.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.31.0
[0.30.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.30.0
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
