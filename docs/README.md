---
afad: "4.0"
version: "0.54.0"
domain: DOCUMENTATION_INDEX
updated: "2026-06-14"
route:
  keywords: [fingrind, docs, index, user-guides, developer-guides, api-reference, schema, examples, sqlite]
  questions: ["where should I start in the fingrind docs", "which docs are user-facing in fingrind", "where are the developer and api docs in fingrind"]
---

# Documentation Index

**Purpose**: Route readers to the right FinGrind documentation set quickly.
**Prerequisites**: None.

## Start Here

Start with the root [README.md](../README.md) for the storefront overview.
If you want the fastest first run, continue with [USER_QUICK_START.md](./USER_QUICK_START.md).
Then choose one of the user, developer, or reference tracks below.

## User Guides

- [USER_QUICK_START.md](./USER_QUICK_START.md): fastest path to create one protected book, post one entry, and read one report back
- [USER_INSTALL.md](./USER_INSTALL.md): exact public bundle names, launcher paths, checksum commands, attestation commands, and container package surface
- [USER_CLI.md](./USER_CLI.md): packaged CLI usage, commands, report output modes, PDF artifact behavior, exit codes, and runtime requirements
- [USER_CONTAINER.md](./USER_CONTAINER.md): published container image workflow, mounted workspace model, and smoke-tested command examples
- [USER_REQUESTS.md](./USER_REQUESTS.md): posting, account-declaration, ledger-plan, read/report JSON shapes, executable request schemas, deterministic error codes, and response envelopes
- [USER_EXAMPLES.md](./USER_EXAMPLES.md): copy-paste command flows for opening books, inspecting compatibility, paging accounts, running office-worker reports, querying committed history, preflight, commit, atomic ledger plans, duplicates, stdin, and reversal templates

The checked-in `examples/*` files below are source-checkout fixtures for review and copying.
Public release bundles do not include the repository's `docs/examples/` tree.
- [examples/basic-posting-request.json](./examples/basic-posting-request.json): minimal valid request payload
- [examples/request-template.json](./examples/request-template.json): exact `print-request-template` scaffold capture
- [examples/declare-account-supplemental-cash-reserve.json](./examples/declare-account-supplemental-cash-reserve.json): supplemental account-declaration request for an additional cash reserve account on top of the seeded starter chart
- [examples/declare-account-supplemental-misc-revenue.json](./examples/declare-account-supplemental-misc-revenue.json): supplemental account-declaration request for an additional miscellaneous revenue account on top of the seeded starter chart
- [examples/unknown-account-request.json](./examples/unknown-account-request.json): posting request that deterministically rejects for an undeclared account
- [examples/account-state-violations-response.json](./examples/account-state-violations-response.json): posting rejection example with aggregated account-state details
- [examples/basic-posting-committed-response.json](./examples/basic-posting-committed-response.json): example committed response with a UUID v7 `postingId`
- [examples/inspect-book-response.json](./examples/inspect-book-response.json): example `inspect-book` compatibility snapshot
- [examples/list-accounts-response.json](./examples/list-accounts-response.json): example paginated account-registry response
- [examples/get-posting-response.json](./examples/get-posting-response.json): example committed-posting lookup response
- [examples/list-postings-response.json](./examples/list-postings-response.json): example paginated posting-history response
- [examples/account-balance-response.json](./examples/account-balance-response.json): example grouped per-currency balance response
- [examples/trial-balance-response.json](./examples/trial-balance-response.json): example JSON trial-balance response
- [examples/account-ledger-response.json](./examples/account-ledger-response.json): example JSON account-ledger response
- [examples/period-summary-response.json](./examples/period-summary-response.json): example JSON period-summary response
- [examples/trial-balance-text.txt](./examples/trial-balance-text.txt): example plain-language trial-balance output
- [examples/account-ledger.csv](./examples/account-ledger.csv): example spreadsheet-ready account-ledger export
- [examples/period-summary-text.txt](./examples/period-summary-text.txt): example plain-language period-summary output

Report PDF artifacts are intentionally not checked in under `docs/examples`; the release and smoke
workflows verify `--pdf-out` directly against real CLI, bundle, and container surfaces.
- [examples/invalid-page-cursor-error.json](./examples/invalid-page-cursor-error.json): deterministic invalid cursor error example
- [examples/protected-book-verification-failed-error.json](./examples/protected-book-verification-failed-error.json): deterministic protected-book verification failure example
- [examples/interactive-prompt-unavailable-error.txt](./examples/interactive-prompt-unavailable-error.txt): deterministic non-interactive prompt failure example
- [examples/ledger-plan-template.json](./examples/ledger-plan-template.json): exact `print-plan-template` scaffold capture
- [examples/ledger-plan-request.json](./examples/ledger-plan-request.json): runnable `execute-plan` request for a fresh book
- [examples/ledger-plan-query-request.json](./examples/ledger-plan-query-request.json): runnable `execute-plan` request that pages accounts and postings inside the plan journal
- [examples/execute-plan-committed-response.json](./examples/execute-plan-committed-response.json): example committed ledger-plan response with `resultDetail: "full"` and a per-step journal
- [examples/execute-plan-assertion-failed-response.json](./examples/execute-plan-assertion-failed-response.json): example failed assertion ledger-plan response with `resultDetail: "full"` and a bounded per-step journal
- [examples/execute-plan-query-response.json](./examples/execute-plan-query-response.json): example committed ledger-plan response with `resultDetail: "full"` whose query steps retain pagination facts and structured row groups
- [examples/reversal-request.json](./examples/reversal-request.json): reversal request template that needs a real prior posting id
- [examples/invalid-empty-lines-request.json](./examples/invalid-empty-lines-request.json): deterministic invalid-request example

## Developer Guides

- [DEVELOPER.md](./DEVELOPER.md): contributor architecture, quality gates, build entrypoints, and cross-module ownership
- [DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md): preferred contributor container workflow and editor-agnostic container use
- [DEVELOPER_AGGREGATES.md](./DEVELOPER_AGGREGATES.md): explicit bookkeeping and workflow consistency boundaries, invariants, and mutation owners
- [ADR_ACCOUNTING_FOUNDATION.md](./ADR_ACCOUNTING_FOUNDATION.md): exact accounting-foundation target doctrine, current maturity, and hard-break implementation order for missing accounting contexts
- [ADR_ACCOUNTING_KERNEL_SCOPE.md](./ADR_ACCOUNTING_KERNEL_SCOPE.md): current bookkeeping-kernel scope, public truth boundaries, and intentional exclusions
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md): canonical bounded contexts, context map, and accounting-entity vocabulary
- [DEVELOPER_DISTRIBUTION.md](./DEVELOPER_DISTRIBUTION.md): bundle layout, public artifact rules, and release-asset expectations
- [DEVELOPER_DOCUMENTATION.md](./DEVELOPER_DOCUMENTATION.md): documentation placement, maintenance, and reference-spine rules
- [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md): contributor Docker posture, smoke workflow, and runtime-container boundaries
- [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md): Gradle architecture, included build logic, wrapper policy, and nested Jazzer build structure
- [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md): host Java 26 setup and wrapper-first contributor posture
- [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md): Jazzer purpose, boundaries, and local-only fuzzing stance
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md): supported Jazzer wrapper commands, findings workflow, and operator recovery paths
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md): committed harness coverage and remaining hostile-input focus areas
- [DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md): GitHub Release publication topology, attestation invariants, Windows ZIP canary behavior, and post-tag workflow repair
- [DEVELOPER_SECURITY.md](./DEVELOPER_SECURITY.md): canonical security model, threat boundary, secret transport, and runtime-identity rules
- [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md): managed SQLite3MC runtime, protected-book format, and storage threat boundary
- [ADR_SQLITE_JOURNAL_MODE.md](./ADR_SQLITE_JOURNAL_MODE.md): why FinGrind pins `journal_mode=DELETE` instead of WAL on the current storage line
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md): first-time GitHub repository bootstrap and workflow bring-up
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md): release preparation, tag verification, and public artifact publication flow

## Reference And Schema

- [DOC_00_Index.md](./DOC_00_Index.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_01_Core_EvidenceAndWire.md](./DOC_01_Core_EvidenceAndWire.md)
- [DOC_01_DecimalBoundaries.md](./DOC_01_DecimalBoundaries.md)
- [DOC_02_Application.md](./DOC_02_Application.md)
- [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md)
- [DOC_02_AdministrationAndReports.md](./DOC_02_AdministrationAndReports.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)
- [DOC_04_CliAndPdfAdapters.md](./DOC_04_CliAndPdfAdapters.md)
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)
