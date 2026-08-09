---
afad: "5.0.1"
version: "0.62.2"
domain: DOCUMENTATION_INDEX
updated: "2026-08-09"
route:
  keywords: [fingrind, docs, index, user-guides, developer-guides, api-reference, schema, examples, sqlite]
  questions: ["where should I start in the fingrind docs", "which docs are user-facing in fingrind", "where are the developer and api docs in fingrind"]
---

# Documentation Index

**Purpose**: Route readers to the right FinGrind documentation set quickly.
**Prerequisites**: None.

## Start Here

Start with the root [README.md](../README.md) for the storefront overview, or continue with [USER_QUICK_START.md](./USER_QUICK_START.md) for the fastest first run. Then choose one of the user, developer, or reference tracks below.

## User Guides

- [USER_QUICK_START.md](./USER_QUICK_START.md): fastest path to create one protected book, post one entry, and read one report back
- [USER_INSTALL.md](./USER_INSTALL.md): exact public bundle names, launcher paths, checksum commands, attestation commands, and container package surface
- [USER_CLI.md](./USER_CLI.md): packaged CLI usage, commands, report output modes, PDF artifact behavior, exit codes, and runtime requirements
- [USER_BOOK_ATTESTATION.md](./USER_BOOK_ATTESTATION.md): current protected-book attestation, credential, verification, backup, and retained-receipt behavior
- [USER_CLI_OPERATIONAL_NOTES.md](./USER_CLI_OPERATIONAL_NOTES.md): cross-command diagnostics, protected-book handling, query and report output, runtime facts, and failure boundaries
- [USER_CONTAINER.md](./USER_CONTAINER.md): published container image workflow, mounted workspace model, and smoke-tested command examples
- [USER_REQUESTS.md](./USER_REQUESTS.md): posting, account-declaration, and ledger-plan JSON request shapes plus executable request schemas
- [USER_RESPONSES.md](./USER_RESPONSES.md): shared success and error envelopes plus read and discovery payloads
- [USER_MUTATION_RESPONSES.md](./USER_MUTATION_RESPONSES.md): bookkeeping mutation, ledger-plan, attestation, receipt, and typed payroll response facts
- [USER_REJECTIONS.md](./USER_REJECTIONS.md): deterministic rejection and repair-diagnostic catalog
- [USER_REPORT_RESPONSES.md](./USER_REPORT_RESPONSES.md): report payload spine, resolved-query semantics, and CSV/PDF output contract
- [USER_EXAMPLES.md](./USER_EXAMPLES.md): copy-paste command flows for opening books, inspecting compatibility, paging accounts, running office-worker reports, querying committed history, preflight, commit, and atomic ledger plans
- [USER_ENTRY_WORKFLOWS.md](./USER_ENTRY_WORKFLOWS.md): copy-paste workflows for safe retries, standard-input requests, reversals, and deterministic posting or runtime failure recovery

The checked-in `examples/*` files below are source-checkout fixtures for review and copying.
Public release bundles do not include the repository's `docs/examples/` tree.
- [examples/basic-posting-request.json](./examples/basic-posting-request.json): primary sale posting example
- [examples/request-template.json](./examples/request-template.json): checked-in source-copy companion for the `print-request-template` minimal sale scaffold
- [examples/declare-account-supplemental-cash-reserve.json](./examples/declare-account-supplemental-cash-reserve.json): supplemental account-declaration request for an additional cash reserve account on top of the seeded accounts
- [examples/declare-account-supplemental-misc-revenue.json](./examples/declare-account-supplemental-misc-revenue.json): supplemental account-declaration request for an additional miscellaneous revenue account on top of the seeded accounts
- [examples/unknown-account-request.json](./examples/unknown-account-request.json): typed posting request that deterministically rejects for an undeclared account
- [examples/account-state-violations-response.json](./examples/account-state-violations-response.json): machine rejection example with a stable family summary plus aggregated account-state details
- [examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt): operator-facing text rejection example with one `Summary` header plus one typed issue section per account-state violation
- [examples/entry-semantics-multi-violation-request.json](./examples/entry-semantics-multi-violation-request.json): typed posting request that deterministically rejects with multiple entry-semantics violations
- [examples/entry-semantics-violations-response.json](./examples/entry-semantics-violations-response.json): machine rejection example with ordered entry-semantics `details.violations[]` items
- [examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt): operator-facing text rejection example with one `Summary` header plus one typed issue section per entry-semantics violation
- [examples/basic-posting-committed-response.json](./examples/basic-posting-committed-response.json): example committed response with a UUID v7 `postingId` plus nested `resolvedJournal` classification facts
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
- [examples/unsupported-book-format-version-error.json](./examples/unsupported-book-format-version-error.json): deterministic non-current authenticated FinGrind book-format failure example
- [examples/pair-targets-conflict-rejection.json](./examples/pair-targets-conflict-rejection.json): deterministic protected-book pair target-conflict rejection example
- [examples/source-artifact-identity-duplicated-rejection.json](./examples/source-artifact-identity-duplicated-rejection.json): deterministic hard-link source-identity rejection before target admission
- [examples/source-artifact-identity-changed-rejection.json](./examples/source-artifact-identity-changed-rejection.json): deterministic post-lock source-substitution rejection before target admission
- [examples/maintenance-recovery-pending-error.json](./examples/maintenance-recovery-pending-error.json): verified retained pair workflow that must resume with its complete original inputs
- [examples/protected-book-pair-publication-uncertain-error.json](./examples/protected-book-pair-publication-uncertain-error.json): recoverable protected-book pair completion-uncertainty example
- [examples/protected-book-pair-publication-evidence-blocked-error.json](./examples/protected-book-pair-publication-evidence-blocked-error.json): retained pair evidence that must be independently investigated rather than rerun
- [examples/open-book-preparation-artifacts-retained-error.json](./examples/open-book-preparation-artifacts-retained-error.json): incomplete book-opening attempt that retained every created artifact as evidence
- [examples/interactive-prompt-unavailable-error.txt](./examples/interactive-prompt-unavailable-error.txt): deterministic non-interactive prompt failure example
- [examples/ledger-plan-template.json](./examples/ledger-plan-template.json): checked-in source-copy companion for the default `print-plan-template` general ledger-plan scaffold
- [examples/ledger-plan-request.json](./examples/ledger-plan-request.json): primary runnable `execute-plan` request that establishes tax accounts and a tax registration atomically
- [examples/ledger-plan-query-request.json](./examples/ledger-plan-query-request.json): follow-on `execute-plan` request that pages the initialized account registry
- [examples/execute-plan-committed-response.json](./examples/execute-plan-committed-response.json): example committed ledger-plan response with `resultDetail: "full"` and a per-step journal
- [examples/execute-plan-assertion-failed-response.json](./examples/execute-plan-assertion-failed-response.json): example failed assertion ledger-plan response with `resultDetail: "full"` and a bounded per-step journal
- [examples/execute-plan-query-response.json](./examples/execute-plan-query-response.json): example credential-free read-only ledger-plan response with `resultDetail: "full"` whose query steps retain pagination facts and structured row groups
- [examples/execute-plan-no-durable-child-mutation-response.json](./examples/execute-plan-no-durable-child-mutation-response.json): example signed all-replay ledger-plan response whose explicit disposition proves mutation-capable execution without a new aggregate operation
- [examples/reversal-request.json](./examples/reversal-request.json): reversal request template that needs a real prior posting id
- [examples/invalid-empty-lines-request.json](./examples/invalid-empty-lines-request.json): deterministic invalid-request example

## Developer Guides

- [DEVELOPER.md](./DEVELOPER.md): contributor architecture, quality gates, build entrypoints, and cross-module ownership
- [DEVELOPER_DEVCONTAINER.md](./DEVELOPER_DEVCONTAINER.md): preferred contributor container workflow and editor-agnostic container use
- [DEVELOPER_AGGREGATES.md](./DEVELOPER_AGGREGATES.md): explicit bookkeeping and workflow consistency boundaries, invariants, and mutation owners
- [ADR_ACCOUNTING_FOUNDATION.md](./ADR_ACCOUNTING_FOUNDATION.md): exact accounting-foundation target doctrine, current maturity, and hard-break implementation order for missing accounting contexts
- [ADR_ACCOUNTING_KERNEL_SCOPE.md](./ADR_ACCOUNTING_KERNEL_SCOPE.md): current bookkeeping-kernel scope, public truth boundaries, and intentional exclusions
- [ADR_ACCRUAL_CUTOFFS.md](./ADR_ACCRUAL_CUTOFFS.md): accrual cut-off bounded context, aggregate invariants, persistence facts, and publication boundary
- [ADR_FIXED_ASSETS.md](./ADR_FIXED_ASSETS.md): fixed-assets context boundary, lifecycle invariants, primary reference, and publication gate
- [ADR_FINANCING.md](./ADR_FINANCING.md): financing context boundary, lifecycle invariants, primary reference, and publication gate
- [ADR_LATVIAN_PAYROLL.md](./ADR_LATVIAN_PAYROLL.md): Latvian monthly-payroll bounded context, statutory profile, aggregate invariants, persistence facts, and publication boundary
- [ADR_REALIZED_FOREIGN_EXCHANGE.md](./ADR_REALIZED_FOREIGN_EXCHANGE.md): realized-FX settlement boundary, lifecycle invariants, primary reference, and publication gate
- [ADR_INVENTORY_COSTING.md](./ADR_INVENTORY_COSTING.md): live inventory-costing doctrine, its exact pool-based truth boundary, and the constraints later inventory work must preserve
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md): canonical bounded contexts, context map, and accounting-entity vocabulary
- [DEVELOPER_DISTRIBUTION.md](./DEVELOPER_DISTRIBUTION.md): bundle layout, public artifact rules, and release-asset expectations
- [DEVELOPER_UNSIGNED_DISTRIBUTION.md](./DEVELOPER_UNSIGNED_DISTRIBUTION.md): current unsigned macOS and Windows bundle policy, the quarantine and Mark-of-the-Web gates, and the checksum-plus-attestation trust model
- [DEVELOPER_DOCUMENTATION.md](./DEVELOPER_DOCUMENTATION.md): documentation placement, maintenance, and reference-spine rules
- [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md): contributor Docker posture, smoke workflow, and runtime-container boundaries
- [DEVELOPER_ARCHITECTURE.md](./DEVELOPER_ARCHITECTURE.md): independent architecture module, enforced dependency direction, and responsibility-boundary rules
- [DEVELOPER_CI.md](./DEVELOPER_CI.md): CI gate topology and path-based devcontainer workflow policy
- [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md): Gradle architecture, included build logic, wrapper policy, and nested Jazzer build structure
- [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md): host Java 26 setup and wrapper-first contributor posture
- [DEVELOPER_DEPENDABOT_APPROVAL.md](./DEVELOPER_DEPENDABOT_APPROVAL.md): maintainer decision, required gates, and cleanup policy for Dependabot pull requests
- [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md): Jazzer purpose, boundaries, and local-only fuzzing stance
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md): supported Jazzer wrapper commands, findings workflow, and operator recovery paths
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md): committed harness coverage and remaining hostile-input focus areas
- [DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md): GitHub Release publication topology, attestation invariants, Windows ZIP canary behavior, and post-tag workflow repair
- [RUNBOOK_REMEDIATION_PLAN.md](./RUNBOOK_REMEDIATION_PLAN.md): public Ledger-1 v0.63 remediation projection, independently verifiable historic P0 closure evidence, deterministic regeneration, and recovery
- [DEVELOPER_SECURITY.md](./DEVELOPER_SECURITY.md): canonical security model, threat boundary, secret transport, and runtime-identity rules
- [DEVELOPER_REJECTION_TEXT_SURFACE.md](./DEVELOPER_REJECTION_TEXT_SURFACE.md): review gate for the lean machine posting-rejection envelope and the dedicated per-violation text-mode rendering
- [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md): managed SQLite3MC runtime, protected-book format, and storage threat boundary
- [ADR_SQLITE_JOURNAL_MODE.md](./ADR_SQLITE_JOURNAL_MODE.md): why FinGrind pins `journal_mode=DELETE` instead of WAL on the current storage line
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md): first-time GitHub repository bootstrap and workflow bring-up
- [GITHUB_RELEASE_TAG_GOVERNANCE.md](./GITHUB_RELEASE_TAG_GOVERNANCE.md): release-tag creation authorization and immutable-tag ruleset configuration
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md): release preparation, tag control, hygiene, and primary-checkout reconciliation
- [RELEASE_PUBLICATION_VERIFICATION.md](./RELEASE_PUBLICATION_VERIFICATION.md): post-tag GitHub Release object, asset, and attestation handoff
- [RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md](./RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md): anonymous GHCR availability, manifest identity, mounted-book, and PDF handoff

## Historical Release Notes

- [DOC_CHANGELOG_ARCHIVE_2026_MAY.md](./DOC_CHANGELOG_ARCHIVE_2026_MAY.md): archived release notes for `0.30.0` through `0.23.0`
- [DOC_CHANGELOG_ARCHIVE_2026_APRIL_I.md](./DOC_CHANGELOG_ARCHIVE_2026_APRIL_I.md): archived release notes for `0.22.0` through `0.12.0`
- [DOC_CHANGELOG_ARCHIVE_2026_APRIL_II.md](./DOC_CHANGELOG_ARCHIVE_2026_APRIL_II.md): archived release notes for `0.11.0` through `0.1.0`

## Reference And Schema

- [DOC_00_Index.md](./DOC_00_Index.md)
- [DOC_00_PrimarySources.md](./DOC_00_PrimarySources.md)
- [DOC_00_BookkeepingRead.md](./DOC_00_BookkeepingRead.md)
- [DOC_00_InventoryCosting.md](./DOC_00_InventoryCosting.md)
- [DOC_00_OwnedLifecycleContexts.md](./DOC_00_OwnedLifecycleContexts.md)
- [DOC_00_PostingAndRejections.md](./DOC_00_PostingAndRejections.md)
- [DOC_00_Attestation.md](./DOC_00_Attestation.md)
- [DOC_00_ProtectedBookMaintenance.md](./DOC_00_ProtectedBookMaintenance.md)
- [DOC_00_ResponseAndWorkflow.md](./DOC_00_ResponseAndWorkflow.md)
- [DOC_00_BookSessionsAndAdapters.md](./DOC_00_BookSessionsAndAdapters.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_01_Core_BookDoctrine.md](./DOC_01_Core_BookDoctrine.md)
- [DOC_01_Core_LedgerAndPosting.md](./DOC_01_Core_LedgerAndPosting.md)
- [DOC_01_Core_EvidenceAndWire.md](./DOC_01_Core_EvidenceAndWire.md)
- [DOC_01_DecimalBoundaries.md](./DOC_01_DecimalBoundaries.md)
- [DOC_02_Application.md](./DOC_02_Application.md)
- [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md)
- [DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md): current operation, authorization, preimage, and envelope contract for protected-book format 57
- [DOC_02_VerifiableOperationAttestationEncoding.md](./DOC_02_VerifiableOperationAttestationEncoding.md): current credential-custody, signing, credential-value, and canonical-byte-primitive contract for protected-book format 57
- [DOC_02_VerifiableOperationAttestationVerification.md](./DOC_02_VerifiableOperationAttestationVerification.md): current verifier procedure, compromise review, and structural-failure contract for protected-book format 57
- [DOC_02_VerifiableOperationAttestationProfiles.md](./DOC_02_VerifiableOperationAttestationProfiles.md): current field-level posting profiles and autonomous system-close derivations for protected-book format 57
- [DOC_02_VerifiableOperationAttestationArtifacts.md](./DOC_02_VerifiableOperationAttestationArtifacts.md): current backup-manifest, artifact-publication, restore, receipt, anchor, and artifact-vector contract
- [DOC_02_VerifiableOperationAttestationCorpus.md](./DOC_02_VerifiableOperationAttestationCorpus.md): current positive and negative static fixture source for protected-book format 57
- [DOC_02_VerifiableOperationAttestationVectors.md](./DOC_02_VerifiableOperationAttestationVectors.md): byte-for-byte operation-envelope conformance vectors for protected-book format 57
- [DOC_02_MachineContractAndDescriptors.md](./DOC_02_MachineContractAndDescriptors.md)
- [DOC_02_AdministrationAndReports.md](./DOC_02_AdministrationAndReports.md)
- [DOC_02_PeriodCloseAndRejections.md](./DOC_02_PeriodCloseAndRejections.md)
- [DOC_02_AccountRegistryLifecycle.md](./DOC_02_AccountRegistryLifecycle.md)
- [DOC_02_AccrualCutoffs.md](./DOC_02_AccrualCutoffs.md)
- [DOC_02_OwnedLifecycleContexts.md](./DOC_02_OwnedLifecycleContexts.md)
- [DOC_02_LatvianPayroll.md](./DOC_02_LatvianPayroll.md)
- [DOC_02_BookMaintenanceContracts.md](./DOC_02_BookMaintenanceContracts.md)
- [DOC_02_InventoryValuation.md](./DOC_02_InventoryValuation.md)
- [DOC_02_IncomeStatementPresentation.md](./DOC_02_IncomeStatementPresentation.md)
- [DOC_02_SharedReportModel.md](./DOC_02_SharedReportModel.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)
- [DOC_02_LedgerPlanVocabulary.md](./DOC_02_LedgerPlanVocabulary.md): stable `execute-plan`
  wire vocabulary and aggregate-attestation outcomes
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)
- [DOC_03_SqliteRuntimeAndSessions.md](./DOC_03_SqliteRuntimeAndSessions.md): packaged SQLite
  runtime, failure taxonomy, and workflow-shaped session APIs
- [DEVELOPER_SQLITE_RUNTIME.md](./DEVELOPER_SQLITE_RUNTIME.md): managed SQLite build, distribution,
  FFM rationale, and native-bridge invariants
- [DOC_04_CliAndPdfAdapters.md](./DOC_04_CliAndPdfAdapters.md)
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)
- [sqlite/SCHEMA_CORE_01_FOUNDATION.md](./sqlite/SCHEMA_CORE_01_FOUNDATION.md)
- [sqlite/SCHEMA_CORE_01a_ATTESTATION_OPERATION.md](./sqlite/SCHEMA_CORE_01a_ATTESTATION_OPERATION.md)
- [sqlite/SCHEMA_CORE_02_ACCOUNT_TABLE.md](./sqlite/SCHEMA_CORE_02_ACCOUNT_TABLE.md)
- [sqlite/SCHEMA_CORE_03_ACCOUNT_DECLARATION_RULES.md](./sqlite/SCHEMA_CORE_03_ACCOUNT_DECLARATION_RULES.md)
- [sqlite/SCHEMA_CORE_03a_ACCOUNT_LIFECYCLE_RULES.md](./sqlite/SCHEMA_CORE_03a_ACCOUNT_LIFECYCLE_RULES.md)
- [sqlite/SCHEMA_CORE_03z_TAX_REGISTRATION.md](./sqlite/SCHEMA_CORE_03z_TAX_REGISTRATION.md)
- [sqlite/SCHEMA_CORE_04_POSTING_FACT.md](./sqlite/SCHEMA_CORE_04_POSTING_FACT.md)
- [sqlite/SCHEMA_CORE_04z_POSTING_FACT_ADMISSION.md](./sqlite/SCHEMA_CORE_04z_POSTING_FACT_ADMISSION.md)
- [sqlite/SCHEMA_CORE_05_POSTING_SOURCE_DOCUMENT.md](./sqlite/SCHEMA_CORE_05_POSTING_SOURCE_DOCUMENT.md)
- [sqlite/SCHEMA_CORE_06_POSTING_APPROVAL.md](./sqlite/SCHEMA_CORE_06_POSTING_APPROVAL.md)
- [sqlite/SCHEMA_CORE_06z_POSTING_APPLIED_TAX.md](./sqlite/SCHEMA_CORE_06z_POSTING_APPLIED_TAX.md)
- [sqlite/SCHEMA_CORE_06za_POSTING_FOREIGN_EXCHANGE.md](./sqlite/SCHEMA_CORE_06za_POSTING_FOREIGN_EXCHANGE.md)
- [sqlite/SCHEMA_CORE_07_JOURNAL_LINES.md](./sqlite/SCHEMA_CORE_07_JOURNAL_LINES.md)
- [sqlite/SCHEMA_CORE_07z_INVENTORY_MOVEMENT.md](./sqlite/SCHEMA_CORE_07z_INVENTORY_MOVEMENT.md)
- [sqlite/SCHEMA_CORE_07za_INVENTORY_ON_HAND.md](./sqlite/SCHEMA_CORE_07za_INVENTORY_ON_HAND.md)
- [sqlite/SCHEMA_CORE_08_INTERIM_RESULT_SWEEP_CORE.md](./sqlite/SCHEMA_CORE_08_INTERIM_RESULT_SWEEP_CORE.md)
- [sqlite/SCHEMA_CORE_09_INTERIM_RESULT_SWEEP_LINKS.md](./sqlite/SCHEMA_CORE_09_INTERIM_RESULT_SWEEP_LINKS.md)
- [sqlite/SCHEMA_CORE_10_FISCAL_YEAR_CLOSE_TABLE.md](./sqlite/SCHEMA_CORE_10_FISCAL_YEAR_CLOSE_TABLE.md)
- [sqlite/SCHEMA_CORE_11_FISCAL_YEAR_CLOSE_TARGET_RULES.md](./sqlite/SCHEMA_CORE_11_FISCAL_YEAR_CLOSE_TARGET_RULES.md)
- [sqlite/SCHEMA_CORE_12_FISCAL_YEAR_CLOSE_LINKS.md](./sqlite/SCHEMA_CORE_12_FISCAL_YEAR_CLOSE_LINKS.md)
- [sqlite/SCHEMA_CORE_13_AUDIT_EVENTS.md](./sqlite/SCHEMA_CORE_13_AUDIT_EVENTS.md)
- [sqlite/SCHEMA_CORE_13z_ACCRUAL_CUTOFF.md](./sqlite/SCHEMA_CORE_13z_ACCRUAL_CUTOFF.md)
- [sqlite/SCHEMA_CORE_13za_ACCRUAL_CUTOFF_APPLICATIONS.md](./sqlite/SCHEMA_CORE_13za_ACCRUAL_CUTOFF_APPLICATIONS.md)
- [sqlite/SCHEMA_CORE_13zb_FIXED_ASSETS.md](./sqlite/SCHEMA_CORE_13zb_FIXED_ASSETS.md)
- [sqlite/SCHEMA_CORE_13zc_FINANCING.md](./sqlite/SCHEMA_CORE_13zc_FINANCING.md)
- [sqlite/SCHEMA_CORE_13zd_REALIZED_FOREIGN_EXCHANGE.md](./sqlite/SCHEMA_CORE_13zd_REALIZED_FOREIGN_EXCHANGE.md)
- [sqlite/SCHEMA_CORE_13ze_LATVIAN_PAYROLL_RUNS.md](./sqlite/SCHEMA_CORE_13ze_LATVIAN_PAYROLL_RUNS.md)
- [sqlite/SCHEMA_CORE_13zea_LATVIAN_PAYROLL_RUN_IMMUTABILITY.md](./sqlite/SCHEMA_CORE_13zea_LATVIAN_PAYROLL_RUN_IMMUTABILITY.md)
- [sqlite/SCHEMA_CORE_13zf_LATVIAN_PAYROLL_SETTLEMENTS.md](./sqlite/SCHEMA_CORE_13zf_LATVIAN_PAYROLL_SETTLEMENTS.md)
- [sqlite/SCHEMA_CORE_14_INDEXES_AND_IMMUTABILITY.md](./sqlite/SCHEMA_CORE_14_INDEXES_AND_IMMUTABILITY.md)
