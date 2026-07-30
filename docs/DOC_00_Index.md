---
afad: "5.0.1"
version: "0.62.0"
domain: INDEX
updated: "2026-07-30"
route:
  keywords: [fingrind, index, routing, api, symbols, core, contract, executor, sqlite, cli, report-pdf, machine-contract, book-session, tax, account-registry, account-lifecycle, journal, money, quantity, inventory costing, weighted average, posting]
  questions: ["where is the fingrind api documented", "which doc file covers SqliteBookSessions", "which doc file covers RequestProvenance", "which doc file covers ProtocolCatalog", "which doc file covers PdfReportService", "which doc file covers the tax surface", "which doc file covers account lifecycle", "which doc file covers quantity and weighted-average inventory costing primitives"]
---

# API Index

**Purpose**: Route readers first by bounded context, then by file and symbol.

## Context Routing

- Public bookkeeping protocol:
  use [DOC_02_AdministrationAndReports.md](./DOC_02_AdministrationAndReports.md) for
  administration, inspection, non-inventory queries, and reports; use
  [DOC_02_PeriodCloseAndRejections.md](./DOC_02_PeriodCloseAndRejections.md) for
  interim-result sweep, fiscal-year close, and deterministic administration or read-side
  rejections; use
  [DOC_02_AccountRegistryLifecycle.md](./DOC_02_AccountRegistryLifecycle.md) for account
  amendment and retirement; use
  [DOC_02_BookMaintenanceContracts.md](./DOC_02_BookMaintenanceContracts.md) for public
  protected-book maintenance artifacts, path presentation, and deterministic maintenance
  rejections; use
  [DOC_02_AccrualCutoffs.md](./DOC_02_AccrualCutoffs.md) for accrual-basis prepayments,
  deferred revenue, accrued expenses, lifecycle applications, compensating reversals, and the
  accrual-cutoff schedule; use [DOC_02_OwnedLifecycleContexts.md](./DOC_02_OwnedLifecycleContexts.md)
  for fixed assets, financing, and realized foreign-exchange lifecycle aggregates, derived
  resolution, and register reports; use
  [DOC_02_LatvianPayroll.md](./DOC_02_LatvianPayroll.md) for the deliberately narrow Latvian 2026
  monthly-payroll calculation, immutable payroll-run facts, admission vocabulary, and primary-source
  links; use
  [DOC_02_InventoryValuation.md](./DOC_02_InventoryValuation.md) for point-in-time inventory
  valuation and costed-sale readback; use
  [DOC_02_IncomeStatementPresentation.md](./DOC_02_IncomeStatementPresentation.md) for trading
  income-statement gross-profit and multi-step presentation helpers; use
  [DOC_02_SharedReportModel.md](./DOC_02_SharedReportModel.md) for the shared report content
  model and family builders; use
  [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md) for posting commands,
  posting results, and write-side rejections.
- Public workflow protocol:
  use [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md) for `LedgerPlan`,
  `LedgerStep`, `LedgerAssertion`, and the public `LedgerJournal*` / `LedgerPlanFailure` /
  `LedgerPlanResult` surface.
- Runtime/discovery contract:
  use [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md) for protocol metadata
  and runtime/distribution/storage facts; use
  [DOC_02_MachineContractAndDescriptors.md](./DOC_02_MachineContractAndDescriptors.md) for
  machine-contract assembly, discovery descriptors, templates, and deterministic failures.
- Verifiable operation attestation:
  use [DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md) for
  the current hard-break protected-book contract, including
  credential-purpose authorization and closed per-kind effects. Use
  [DOC_02_VerifiableOperationAttestationEncoding.md](./DOC_02_VerifiableOperationAttestationEncoding.md)
  for credential custody, signing sessions, and canonical primitive encoding. Use
  [DOC_02_VerifiableOperationAttestationVerification.md](./DOC_02_VerifiableOperationAttestationVerification.md)
  for verifier procedure, compromise review, and deterministic structural failures.
  Use [DOC_02_VerifiableOperationAttestationProfiles.md](./DOC_02_VerifiableOperationAttestationProfiles.md)
  for the normative request-to-effect semantic profiles and autonomous system-close derivations.
  Use [DOC_02_VerifiableOperationAttestationArtifacts.md](./DOC_02_VerifiableOperationAttestationArtifacts.md)
  for backup-manifest, artifact-publication, restore, receipt, and artifact-vector contracts.
  Use [DOC_02_VerifiableOperationAttestationCorpus.md](./DOC_02_VerifiableOperationAttestationCorpus.md)
  for the normative positive, negative, backup-artifact, and live-CAS fixture sources.
- Decimal-boundary design:
  use [DOC_01_DecimalBoundaries.md](./DOC_01_DecimalBoundaries.md) for the exact-money boundary,
  the exact-quantity boundary, and the future split between money, rates, percentages, exchange
  rates, and other decimal factors.
- Inventory-costing design:
  use [ADR_INVENTORY_COSTING.md](./ADR_INVENTORY_COSTING.md) for the first inventory operational-subledger doctrine, the exact pool-based costing truth boundary, and the accepted weighted-average implementation line.
- Bookkeeping-kernel scope and intentional exclusions:
  use [ADR_ACCOUNTING_KERNEL_SCOPE.md](./ADR_ACCOUNTING_KERNEL_SCOPE.md) for the current narrow built-in bookkeeping kernel, its public truth boundaries, and its intentional exclusions.
- Local bookkeeping context:
  use [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md) for executor-owned
  sessions plus local bookkeeping read/write models that cross those seams.
  Use [DOC_00_BookSessionsAndAdapters.md](./DOC_00_BookSessionsAndAdapters.md) when routing one
  public session, store, query-view, or adapter symbol to that reference surface.
- Local workflow context:
  use [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md) for local workflow
  plans, steps, assertions, internal workflow journals, and the published-language translator that
  projects them outward. Use [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)
  for the deliberately disjoint signed `LedgerPlanTransaction`, `LedgerPlanMutationStore`, and
  `LedgerPlanExecutionStore` boundary plus the credential-free `LedgerPlanReadStore`,
  `LedgerPlanReadOnlyTransaction`, and `LedgerPlanReadOnlyExecutionStore` boundary. Each keeps
  plan execution inside its own protected-book capability without a compatibility bridge between
  mutation and read-only authority.
- Adapter/runtime seams:
  use [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md) for SQLite-backed
  session/runtime types and [DOC_04_CliAndPdfAdapters.md](./DOC_04_CliAndPdfAdapters.md) for CLI
  and PDF adapter entrypoints.

## File Index

| File | Scope |
|:-----|:------|
| `DOC_00_BookkeepingRead.md` | account-ledger pagination, running-balance, and continuation-boundary symbol-routing continuation |
| `DOC_00_BookSessionsAndAdapters.md` | local book-session, store, query-view, and SQLite-adapter symbol-routing continuation |
| `DOC_00_PrimarySources.md` | primary legislation and authority links for jurisdiction-specific examples and the Latvian payroll profile |
| `DOC_00_InventoryCosting.md` | inventory-costing symbol-routing continuation for inventory account state, admission, movements, and rejections |
| `DOC_00_OwnedLifecycleContexts.md` | accrual cut-off, fixed-asset, financing, and realized-FX symbol-routing continuation |
| `DOC_00_PostingAndRejections.md` | posting commands, local validation, and published rejection symbol-routing continuation |
| `DOC_00_Attestation.md` | protected-book attestation, verifier, artifact, and attestation-result symbol-routing continuation |
| `DOC_00_ProtectedBookMaintenance.md` | protected-book maintenance, passphrase, SQLite session, and runtime symbol-routing continuation |
| `DOC_00_ResponseAndWorkflow.md` | response-envelope and ledger-workflow symbol-routing continuation |
| `DOC_01_Core.md` | exported accounting vocabulary, identity, exact quantity, weighted-average costing, and shared temporal primitives from the `core` module |
| `DOC_01_Core_BookDoctrine.md` | exported book-template, persisted doctrine, inventory-costing selection, and doctrine-display primitives from the `core` module |
| `DOC_01_Core_LedgerAndPosting.md` | exported journal, money, posting, reporting-period, and request-provenance primitives from the `core` module |
| `DOC_01_Core_EvidenceAndWire.md` | exported source-document evidence primitives, reversal lineage, source-channel provenance, and shared wire-vocabulary support from the `core` module |
| `DOC_01_DecimalBoundaries.md` | exact-money boundary, exact-quantity boundary, and future non-money decimal-domain rules |
| `DOC_02_Application.md` | routing overview for the split `contract` and `executor` reference spine |
| `DOC_02_ProtocolAndDiscovery.md` | exported `contract` protocol metadata, runtime/distribution facts, request-field vocabularies, and protected-book format owners |
| `DOC_02_VerifiableOperationAttestation.md` | current operation, authorization, preimage, and envelope contract for protected-book format 57 |
| `DOC_02_VerifiableOperationAttestationEncoding.md` | current credential-custody, signing, credential-value, and canonical-byte-primitive contract for protected-book format 57 |
| `DOC_02_VerifiableOperationAttestationVerification.md` | current verifier procedure, compromise review, and structural-failure contract for protected-book format 57 |
| `DOC_02_VerifiableOperationAttestationProfiles.md` | current field-level posting profiles and autonomous system-close derivations for protected-book format 57 |
| `DOC_02_VerifiableOperationAttestationArtifacts.md` | current backup-manifest, artifact-publication, restore, receipt, anchor, and artifact-vector contract |
| `DOC_02_VerifiableOperationAttestationCorpus.md` | current positive and negative source fixtures, including backup, restore, rekey, system-initiation, and live-CAS coverage |
| `DOC_02_VerifiableOperationAttestationVectors.md` | byte-for-byte operation-envelope conformance vectors for protected-book format 57 |
| `DOC_02_MachineContractAndDescriptors.md` | exported machine-contract assembly, discovery descriptors, templates, workflow scaffolds, and deterministic contract-error owners |
| `DOC_02_AdministrationAndReports.md` | exported administration/query/report models and exported `executor` administration and read services |
| `DOC_02_PeriodCloseAndRejections.md` | executor-owned interim-result sweep and fiscal-year close planning plus deterministic administration and read-side rejections |
| `DOC_02_AccountRegistryLifecycle.md` | exported account amendment and retirement commands, results, and durable-history lifecycle rules |
| `DOC_02_AccrualCutoffs.md` | exported accrual cut-off aggregates, lifecycle admission, schedule reporting, and compensating-reversal rules |
| `DOC_02_OwnedLifecycleContexts.md` | exported fixed-asset, financing, and realized-FX aggregates, lifecycle admission, executor resolution, and register reports |
| `DOC_02_LatvianPayroll.md` | exported Latvian monthly-payroll calculation, typed accrual and settlement entries, admission, durable lifecycle, payroll-register reporting, lookup, and rejection contracts |
| `DOC_02_BookMaintenanceContracts.md` | public protected-book maintenance artifact, path-presentation, and rejection contracts |
| `DOC_02_InventoryValuation.md` | exported point-in-time inventory valuation models, report results, local valuation criteria/views, and costed-sale readback truth |
| `DOC_02_IncomeStatementPresentation.md` | exported trading income-statement presentation helpers that keep gross-profit and multi-step section semantics truthful across projections |
| `DOC_02_SharedReportModel.md` | exported shared report content types plus the family-specific builders that feed every public report projection |
| `DOC_02_PostingAndLedgerPlans.md` | exported posting, rejection, lineage, ledger-plan, and plan-journal models plus exported `executor` write services |
| `DOC_02_LedgerPlanVocabulary.md` | stable `execute-plan` wire vocabulary and aggregate-attestation outcomes |
| `DOC_03_BookSessionsAndAdapters.md` | explicit book-access tuples, committed facts, executor-owned seams, and exported SQLite adapter/runtime types |
| `DOC_03_SqliteRuntimeAndSessions.md` | packaged SQLite runtime, failure taxonomy, and workflow-shaped session APIs |
| `DOC_04_CliAndPdfAdapters.md` | public CLI process entrypoint and exported PDF-report adapter |

## Symbol Routing

Inventory account-state, admission, movement, and rejection symbols continue in
[DOC_00_InventoryCosting.md](./DOC_00_InventoryCosting.md).

Account-ledger pagination and continuation symbols continue in
[DOC_00_BookkeepingRead.md](./DOC_00_BookkeepingRead.md).

Response-envelope and ledger-workflow symbols continue in
[DOC_00_ResponseAndWorkflow.md](./DOC_00_ResponseAndWorkflow.md).

Posting-command and rejection symbols continue in
[DOC_00_PostingAndRejections.md](./DOC_00_PostingAndRejections.md).

Protected-book attestation, verification, artifact, and attestation-result symbols continue in
[DOC_00_Attestation.md](./DOC_00_Attestation.md).

Protected-book maintenance, passphrase, SQLite session, and runtime symbols continue in
[DOC_00_ProtectedBookMaintenance.md](./DOC_00_ProtectedBookMaintenance.md).

Local book-session, store, query-view, and SQLite-adapter symbols continue in
[DOC_00_BookSessionsAndAdapters.md](./DOC_00_BookSessionsAndAdapters.md).

| Symbol | File | Section |
|:-------|:-----|:--------|
| `AccountCode` | `DOC_01_Core.md` | `AccountCode` |
| `AccountCodePolicy` | `DOC_01_Core.md` | `AccountCodePolicy` |
| `AccountCodePolicy.ChartStructure` | `DOC_01_Core.md` | `AccountCodePolicy.ChartStructure` |
| `AccountCodePolicy.Meaning` | `DOC_01_Core.md` | `AccountCodePolicy.Meaning` |
| `AccountName` | `DOC_01_Core.md` | `AccountName` |
| `AccountNodeKind` | `DOC_01_Core.md` | `AccountNodeKind` |
| `AccountClassificationReachability` | `DOC_01_Core.md` | `AccountClassificationReachability` |
| `AccountClassificationReachability.ReachabilityCell` | `DOC_01_Core.md` | `AccountClassificationReachability.ReachabilityCell` |
| `AccountStructureDoctrine` | `DOC_01_Core.md` | `AccountStructureDoctrine` |
| `AccountTaxonomy` | `DOC_01_Core.md` | `AccountTaxonomy` |
| `AccountTaxonomyDoctrine` | `DOC_01_Core.md` | `AccountTaxonomyDoctrine` |
| `AccountType` | `DOC_01_Core.md` | `AccountType` |
| `AccountAmendmentOutcome` | `DOC_02_AccountRegistryLifecycle.md` | `AccountAmendmentOutcome`, `AccountRegistryDependency`, `AccountRegistryLifecyclePolicy`, `AccountRegistryLifecycleRejection`, `AccountRegistryPublishedLanguageTranslator`, And `AccountRetirementOutcome` |
| `AccountRegistryDependency` | `DOC_02_AccountRegistryLifecycle.md` | `AccountAmendmentOutcome`, `AccountRegistryDependency`, `AccountRegistryLifecyclePolicy`, `AccountRegistryLifecycleRejection`, `AccountRegistryPublishedLanguageTranslator`, And `AccountRetirementOutcome` |
| `AccountRegistryLifecyclePolicy` | `DOC_02_AccountRegistryLifecycle.md` | `AccountAmendmentOutcome`, `AccountRegistryDependency`, `AccountRegistryLifecyclePolicy`, `AccountRegistryLifecycleRejection`, `AccountRegistryPublishedLanguageTranslator`, And `AccountRetirementOutcome` |
| `AccountRegistryLifecycleRejection` | `DOC_02_AccountRegistryLifecycle.md` | `AccountAmendmentOutcome`, `AccountRegistryDependency`, `AccountRegistryLifecyclePolicy`, `AccountRegistryLifecycleRejection`, `AccountRegistryPublishedLanguageTranslator`, And `AccountRetirementOutcome` |
| `AccountRegistryPublishedLanguageTranslator` | `DOC_02_AccountRegistryLifecycle.md` | `AccountAmendmentOutcome`, `AccountRegistryDependency`, `AccountRegistryLifecyclePolicy`, `AccountRegistryLifecycleRejection`, `AccountRegistryPublishedLanguageTranslator`, And `AccountRetirementOutcome` |
| `AccountRetirementOutcome` | `DOC_02_AccountRegistryLifecycle.md` | `AccountAmendmentOutcome`, `AccountRegistryDependency`, `AccountRegistryLifecyclePolicy`, `AccountRegistryLifecycleRejection`, `AccountRegistryPublishedLanguageTranslator`, And `AccountRetirementOutcome` |
| `AmendAccountCommand` | `DOC_02_AccountRegistryLifecycle.md` | `AmendAccountCommand`, `AmendAccountResult`, `RetireAccountCommand`, And `RetireAccountResult` |
| `AmendAccountResult` | `DOC_02_AccountRegistryLifecycle.md` | `AmendAccountCommand`, `AmendAccountResult`, `RetireAccountCommand`, And `RetireAccountResult` |
| `AccountingBasis` | `DOC_01_Core.md` | `AccountingBasis` |
| `AccountRole` | `DOC_01_Core_LedgerAndPosting.md` | `AccountRole` |
| `AccountStateViolationDetail` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection`, `PostingInventoryRejectionSemantics`, And `PostingRejectionSemantics` |
| `AcceptedPosting` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure` |
| `AccountingEvidence` | `DOC_01_Core.md` | `AccountingEvidence` |
| `AccountingFrameworkPosition` | `DOC_01_Core.md` | `AccountingFrameworkPosition` |
| `AccountingKernelProfileId` | `DOC_01_Core.md` | `AccountingKernelProfileId` |
| `AccountingKernelProfiles` | `DOC_01_Core.md` | `AccountingKernelProfiles` |
| `PostingAccrualCutoffRejectionSemantics` | `DOC_02_AccrualCutoffs.md` | `AccrualCutoffBookkeepingEntryVariants`, `AccrualCutoffAdmissionPolicy`, `AccrualCutoffEntrySemanticsViolations`, And `PostingAccrualCutoffRejectionSemantics` |
| `PostingFixedAssetRejectionSemantics` | `DOC_02_OwnedLifecycleContexts.md` | `PostingFixedAssetRejectionSemantics`, `PostingFinancingRejectionSemantics`, And `PostingRealizedForeignExchangeRejectionSemantics` |
| `PostingFinancingRejectionSemantics` | `DOC_02_OwnedLifecycleContexts.md` | `PostingFixedAssetRejectionSemantics`, `PostingFinancingRejectionSemantics`, And `PostingRealizedForeignExchangeRejectionSemantics` |
| `PostingRealizedForeignExchangeRejectionSemantics` | `DOC_02_OwnedLifecycleContexts.md` | `PostingFixedAssetRejectionSemantics`, `PostingFinancingRejectionSemantics`, And `PostingRealizedForeignExchangeRejectionSemantics` |
| `ContractLatvianPayrollTemplates` | `DOC_02_LatvianPayroll.md` | `ContractLatvianPayrollTemplates` And Its Request Descriptors |
| `LatvianMonthlyPayroll2026` | `DOC_02_LatvianPayroll.md` | `LatvianMonthlyPayroll2026` And `LatvianMonthlyPayrollCalculation` |
| `LatvianMonthlyPayrollCalculation` | `DOC_02_LatvianPayroll.md` | `LatvianMonthlyPayroll2026` And `LatvianMonthlyPayrollCalculation` |
| `LatvianPayrollWithholdingProfile` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollWithholdingProfile` |
| `LatvianPayrollAdmissionPolicy` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollBookkeepingEntryVariants`, `LatvianPayrollAdmissionPolicy`, And `PostingLatvianPayrollRejectionSemantics` |
| `LatvianPayrollAdmissionPolicy.Resolution` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollBookkeepingEntryVariants`, `LatvianPayrollAdmissionPolicy`, And `PostingLatvianPayrollRejectionSemantics` |
| `LatvianPayrollBookkeepingEntryVariants` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollBookkeepingEntryVariants`, `LatvianPayrollAdmissionPolicy`, And `PostingLatvianPayrollRejectionSemantics` |
| `LatvianPayrollEmployeeReference` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRunId`, `LatvianPayrollEmployeeReference`, And `LatvianPayrollMonth` |
| `LatvianPayrollLookupStore` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRunRecord`, `LatvianPayrollSettlementRecord`, And `LatvianPayrollLookupStore` |
| `LatvianPayrollMonth` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRunId`, `LatvianPayrollEmployeeReference`, And `LatvianPayrollMonth` |
| `LatvianPayrollRunId` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRunId`, `LatvianPayrollEmployeeReference`, And `LatvianPayrollMonth` |
| `LatvianPayrollRunRecord` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRunRecord`, `LatvianPayrollSettlementRecord`, And `LatvianPayrollLookupStore` |
| `LatvianPayrollSettlementAdmissionPolicy` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollSettlementKind`, `ResolvedLatvianPayrollSettlement`, And `LatvianPayrollSettlementAdmissionPolicy` |
| `LatvianPayrollSettlementAdmissionPolicy.Resolution` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollSettlementKind`, `ResolvedLatvianPayrollSettlement`, And `LatvianPayrollSettlementAdmissionPolicy` |
| `LatvianPayrollSettlementKind` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollSettlementKind`, `ResolvedLatvianPayrollSettlement`, And `LatvianPayrollSettlementAdmissionPolicy` |
| `LatvianPayrollSettlementRecord` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRunRecord`, `LatvianPayrollSettlementRecord`, And `LatvianPayrollLookupStore` |
| `LatvianPayrollRegisterQuery` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRegisterQuery`, `LatvianPayrollRegisterReport`, And `BookkeepingLatvianPayrollReadService` |
| `LatvianPayrollRegisterReport` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRegisterQuery`, `LatvianPayrollRegisterReport`, And `BookkeepingLatvianPayrollReadService` |
| `LatvianPayrollRegisterReportModelBuilder` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRegisterQuery`, `LatvianPayrollRegisterReport`, And `BookkeepingLatvianPayrollReadService` |
| `LatvianPayrollRegisterResult` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRegisterQuery`, `LatvianPayrollRegisterReport`, And `BookkeepingLatvianPayrollReadService` |
| `LatvianPayrollRegisterRow` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRegisterQuery`, `LatvianPayrollRegisterReport`, And `BookkeepingLatvianPayrollReadService` |
| `LatvianPayrollSettlementStatus` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollRegisterQuery`, `LatvianPayrollRegisterReport`, And `BookkeepingLatvianPayrollReadService` |
| `PostingLatvianPayrollRejectionSemantics` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollBookkeepingEntryVariants`, `LatvianPayrollAdmissionPolicy`, And `PostingLatvianPayrollRejectionSemantics` |
| `ResolvedLatvianPayrollSettlement` | `DOC_02_LatvianPayroll.md` | `LatvianPayrollSettlementKind`, `ResolvedLatvianPayrollSettlement`, And `LatvianPayrollSettlementAdmissionPolicy` |
| `ResolvedAccrualCutoffApplication` | `DOC_02_AccrualCutoffs.md` | `ResolvedAccrualCutoffApplication` |
| `AnchorEntry` | `DOC_01_Core_LedgerAndPosting.md` | `AnchorEntry` |
| `ApprovalDecision` | `DOC_01_Core.md` | `ApprovalDecision` |
| `ApprovalId` | `DOC_01_Core.md` | `ApprovalId` |
| `ApprovalReference` | `DOC_01_Core.md` | `ApprovalReference` |
| `ApprovalType` | `DOC_01_Core.md` | `ApprovalType` |
| `AttestationBookInspection` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationCommit` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `AttestationCommitProjection` | `DOC_03_BookSessionsAndAdapters.md` | `AttestationCommitProjection` |
| `AttestationRegistryInspection` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryInspection.CapabilityPolicy` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryInspection.Credential` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryInspection.PrincipalCapability` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryInspection.SystemWorkflowPolicy` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationInterimResultSweepEffect` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationInterimResultSweepEffect` |
| `AttestationReviewWindowException` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationReviewWindowException` |
| `BalanceMath` | `DOC_01_Core.md` | `BalanceMath` |
| `BalanceSide` | `DOC_01_Core.md` | `BalanceSide` |
| `BookDoctrine` | `DOC_01_Core_BookDoctrine.md` | `BookDoctrine` |
| `BookDoctrineDisplay` | `DOC_01_Core_BookDoctrine.md` | `BookDoctrineDisplay` |
| `BookDoctrines` | `DOC_01_Core_BookDoctrine.md` | `BookDoctrines` |
| `BookEntityName` | `DOC_01_Core.md` | `BookEntityName` |
| `BookIdentity` | `DOC_01_Core.md` | `BookIdentity` |
| `BookTemplateId` | `DOC_01_Core_BookDoctrine.md` | `BookTemplateId` |
| `CanonicalTemporalText` | `DOC_01_Core.md` | `CanonicalTemporalText` |
| `CausationId` | `DOC_01_Core.md` | `CausationId` |
| `CommandId` | `DOC_01_Core.md` | `CommandId` |
| `CommittedProvenance` | `DOC_01_Core.md` | `CommittedProvenance` |
| `ComparativeMode` | `DOC_02_AdministrationAndReports.md` | `ComparativeRangeResolver` |
| `ComparativeRangeResolver` | `DOC_02_AdministrationAndReports.md` | `ComparativeRangeResolver` |
| `ComparativeSelection` | `DOC_02_AdministrationAndReports.md` | `ComparativeRangeResolver` |
| `ClassificationResult` | `DOC_01_Core_LedgerAndPosting.md` | `ClassificationResult` |
| `CorrelationId` | `DOC_01_Core.md` | `CorrelationId` |
| `CryptographicPrimitives` | `DOC_01_Core.md` | `CryptographicPrimitives` |
| `SystemUtcClock` | `DOC_01_Core.md` | `SystemUtcClock` |
| `CurrencyBalance` | `DOC_01_Core_LedgerAndPosting.md` | `CurrencyBalance` |
| `CurrencyUnit` | `DOC_01_Core.md` | `CurrencyUnit` |
| `EffectiveDateRange` | `DOC_01_Core.md` | `EffectiveDateRange` |
| `EffectiveDateHorizonPolicy` | `DOC_01_Core.md` | `EffectiveDateHorizonPolicy` |
| `EffectiveDateHorizonPolicy.FutureEffectiveDateException` | `DOC_01_Core.md` | `EffectiveDateHorizonPolicy` |
| `EntityProfile` | `DOC_01_Core.md` | `EntityProfile` |
| `EntityForm` | `DOC_01_Core.md` | `EntityForm` |
| `EconomicEventClass` | `DOC_01_Core_LedgerAndPosting.md` | `EconomicEventClass` |
| `ForeignExchangeDetails` | `DOC_02_ProtocolAndDiscovery.md` | `ForeignExchangeDetails`, `ForeignExchangeTreatmentKind`, And `QuotedExchangeRate` |
| `ForeignExchangeTreatmentKind` | `DOC_02_ProtocolAndDiscovery.md` | `ForeignExchangeDetails`, `ForeignExchangeTreatmentKind`, And `QuotedExchangeRate` |
| `FinancialPositionLineClassification` | `DOC_01_Core.md` | `FinancialPositionLineClassification` |
| `FiscalYearStart` | `DOC_01_Core.md` | `FiscalYearStart` |
| `IdempotencyKey` | `DOC_01_Core.md` | `IdempotencyKey` |
| `JournalClassifier` | `DOC_01_Core_LedgerAndPosting.md` | `JournalClassifier` |
| `JournalClassifier.AccountRoleLookup` | `DOC_01_Core_LedgerAndPosting.md` | `JournalClassifier` |
| `RequestFingerprint` | `DOC_01_Core.md` | `RequestFingerprint` |
| `JournalEntry` | `DOC_01_Core_LedgerAndPosting.md` | `JournalEntry` |
| `JournalEntryValidationException` | `DOC_01_Core_LedgerAndPosting.md` | `JournalEntryValidationException` |
| `JournalLine` | `DOC_01_Core_LedgerAndPosting.md` | `JournalLine` |
| `JournalLine.EntrySide` | `DOC_01_Core_LedgerAndPosting.md` | `JournalLine.EntrySide` |
| `Money` | `DOC_01_Core_LedgerAndPosting.md` | `Money` |
| `NormalBalance` | `DOC_01_Core_LedgerAndPosting.md` | `NormalBalance` |
| `PositiveMoney` | `DOC_01_Core_LedgerAndPosting.md` | `PositiveMoney` |
| `PostingCoverage` | `DOC_01_Core_LedgerAndPosting.md` | `PostingCoverage` |
| `PostingId` | `DOC_01_Core_LedgerAndPosting.md` | `PostingId` |
| `PostingKind` | `DOC_01_Core_LedgerAndPosting.md` | `PostingKind` |
| `PostingOriginKind` | `DOC_01_Core_LedgerAndPosting.md` | `PostingOriginKind` |
| `ProfitAndLossAccountDoctrine` | `DOC_01_Core.md` | `ProfitAndLossAccountDoctrine` |
| `ProfitAndLossLineClassification` | `DOC_01_Core.md` | `ProfitAndLossLineClassification` |
| `Quantity` | `DOC_01_Core.md` | `Quantity` |
| `ReportingPeriod` | `DOC_01_Core.md` | `ReportingPeriod` |
| `RequestProvenance` | `DOC_01_Core_LedgerAndPosting.md` | `RequestProvenance` |
| `ReversalReason` | `DOC_01_Core_EvidenceAndWire.md` | `ReversalReason` |
| `ReversalReference` | `DOC_01_Core_EvidenceAndWire.md` | `ReversalReference` |
| `ReversalTargetIsReversal` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection`, `PostingInventoryRejectionSemantics`, And `PostingRejectionSemantics` |
| `RetireAccountCommand` | `DOC_02_AccountRegistryLifecycle.md` | `AmendAccountCommand`, `AmendAccountResult`, `RetireAccountCommand`, And `RetireAccountResult` |
| `RetireAccountResult` | `DOC_02_AccountRegistryLifecycle.md` | `AmendAccountCommand`, `AmendAccountResult`, `RetireAccountCommand`, And `RetireAccountResult` |
| `SourceChannel` | `DOC_01_Core_EvidenceAndWire.md` | `SourceChannel` |
| `SourceDocumentId` | `DOC_01_Core_EvidenceAndWire.md` | `SourceDocumentId` |
| `SourceDocumentReference` | `DOC_01_Core_EvidenceAndWire.md` | `SourceDocumentReference` |
| `SourceDocumentType` | `DOC_01_Core_EvidenceAndWire.md` | `SourceDocumentType` |
| `SourceDocumentTypePolicyMode` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `StatementLineKind` | `DOC_01_Core.md` | `StatementLineKind` |
| `WireValue` | `DOC_01_Core_EvidenceAndWire.md` | `WireValue` |
| `BookkeepingKernelFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `ApplicationIdentity` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `AppliedTax` | `DOC_02_PostingAndLedgerPlans.md` | `TaxSelection` And `AppliedTax` |
| `ArtifactOutputDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `BookAccountRegistryFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookBoundaryFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookCipher` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `BookCredentialFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookCurrencyScopeFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookEntityScopeFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookFilesystemFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookFormatContract` | `DOC_02_ProtocolAndDiscovery.md` | `BookFormatContract` |
| `BookInitializationFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookModelFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookProtectionMode` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `CapabilitiesDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `CapabilityCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `CapabilityCatalog`, `CapabilityCatalogEntry`, And `CapabilityStatus` |
| `CapabilityCatalogEntry` | `DOC_02_ProtocolAndDiscovery.md` | `CapabilityCatalog`, `CapabilityCatalogEntry`, And `CapabilityStatus` |
| `CapabilityStatus` | `DOC_02_ProtocolAndDiscovery.md` | `CapabilityCatalog`, `CapabilityCatalogEntry`, And `CapabilityStatus` |
| `CashFlowAssetClassification` | `DOC_01_Core.md` | `CashFlowAssetClassification` And `CashFlowSectionKind` |
| `CashFlowRow` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementQuery`, `CashFlowRow`, `CashFlowSection`, `CashFlowStatementReport`, And `CashFlowStatementResult` |
| `CashFlowRowView` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementCriteria`, `CashFlowRowView`, `CashFlowSectionView`, And `CashFlowStatementView` |
| `CashFlowSection` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementQuery`, `CashFlowRow`, `CashFlowSection`, `CashFlowStatementReport`, And `CashFlowStatementResult` |
| `CashFlowSectionKind` | `DOC_01_Core.md` | `CashFlowAssetClassification` And `CashFlowSectionKind` |
| `CashFlowSectionView` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementCriteria`, `CashFlowRowView`, `CashFlowSectionView`, And `CashFlowStatementView` |
| `CashFlowStatementCriteria` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementCriteria`, `CashFlowRowView`, `CashFlowSectionView`, And `CashFlowStatementView` |
| `CashFlowStatementQuery` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementQuery`, `CashFlowRow`, `CashFlowSection`, `CashFlowStatementReport`, And `CashFlowStatementResult` |
| `CashFlowStatementReport` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementQuery`, `CashFlowRow`, `CashFlowSection`, `CashFlowStatementReport`, And `CashFlowStatementResult` |
| `CashFlowStatementResult` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementQuery`, `CashFlowRow`, `CashFlowSection`, `CashFlowStatementReport`, And `CashFlowStatementResult` |
| `CashFlowStatementView` | `DOC_02_AdministrationAndReports.md` | `CashFlowStatementCriteria`, `CashFlowRowView`, `CashFlowSectionView`, And `CashFlowStatementView` |
| `CommandCatalogDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `CommandDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractDecision` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractAttestationRegistryTemplates` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationRegistryTemplates.AlterPolicyTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationRegistryTemplates.CapabilityGrantTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationRegistryTemplates.EnrollKeyTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationRegistryTemplates.PolicyRuleTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationRegistryTemplates.RevokeKeyTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationRegistryTemplates.RolloverKeyTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationRegistryTemplates.SystemWorkflowPolicyTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationReviewTemplates` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationReviewTemplates.AttestationReviewFileTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractAttestationReviewTemplates.CompromiseReviewTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `ContractDiscovery` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractDiscoveryDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractErrors` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractErrors.Descriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailure` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailureDetails` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailurePaths` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailureException` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContraAccountInvalid` | `DOC_02_AccountRegistryLifecycle.md` | `ContraAccountInvalid` |
| `ContraAccountRelationshipViolation` | `DOC_01_Core.md` | `ContraAccountRelationshipViolation` |
| `DiscoveryFocus` | `DOC_02_ProtocolAndDiscovery.md` | `DiscoveryFocus` |
| `EvidenceClass` | `DOC_01_Core_LedgerAndPosting.md` | `EvidenceClass` |
| `ContractRequestShapes` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.DeclareAccountRequestShapeDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.DeclareTaxRegistrationRequestShapeDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.RetireAccountRequestShapeDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractPostingRequestTemplates`, `ContractRequestShapes.RetireAccountRequestShapeDescriptor`, And `ContractTemplates.RetireAccountTemplateDescriptor` |
| `ContractRequestShapes.EntryKindSemanticsDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.EvidenceRequirementDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.EnumVocabularyDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.LedgerPlanRequestShapeDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.ReachabilityCellDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.RequestFieldDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.RequestInputDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.RequestShapeDescriptorType` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractRequestShapes.RequestShapesDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractReversalTemplates` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractPostingRequestTemplates` | `DOC_02_MachineContractAndDescriptors.md` | `ContractPostingRequestTemplates`, `ContractRequestShapes.RetireAccountRequestShapeDescriptor`, And `ContractTemplates.RetireAccountTemplateDescriptor` |
| `ContractTemplates` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.AccountingEvidenceTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.ApprovalTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.DeclareAccountTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.DeclareTaxCodeTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.DeclareTaxRegistrationTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `InventoryReliefTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ForeignExchangeTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.JournalLineTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractSettlementTemplates` | `DOC_02_MachineContractAndDescriptors.md` | `ContractSettlementTemplates` |
| `ContractPlanTemplates` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractPlanTemplates.LedgerAssertionTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractPlanTemplates.LedgerPlanStepTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractPlanTemplates.LedgerPlanTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.OpeningBalanceTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.ProvenanceTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.RecognitionIntervalTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractTemplates.RecognitionIntervalTemplateDescriptor` |
| `ContractTemplates.RetireAccountTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractPostingRequestTemplates`, `ContractRequestShapes.RetireAccountRequestShapeDescriptor`, And `ContractTemplates.RetireAccountTemplateDescriptor` |
| `QuotedExchangeRateTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractTemplates.SourceDocumentTemplateDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `TemplateDescriptorType` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `TemporalScopeArchetype` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `UnitOfMeasure` | `DOC_01_Core.md` | `UnitOfMeasure` |
| `UnitOfMeasure.QuantityIncompatibleWithUnitOfMeasureException` | `DOC_01_Core.md` | `UnitOfMeasure.QuantityIncompatibleWithUnitOfMeasureException` |
| `WeightedAverageCostingMath` | `DOC_01_Core.md` | `WeightedAverageCostingMath` |
| `WeightedAverageCostingMath.Disposal` | `DOC_01_Core.md` | `WeightedAverageCostingMath.Disposal` |
| `WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException` | `DOC_01_Core.md` | `WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException` |
| `WeightedAverageCostingMath.InexactAcquisitionCostException` | `DOC_01_Core.md` | `WeightedAverageCostingMath.InexactAcquisitionCostException` |
| `WeightedAverageCostingMath.InventoryPool` | `DOC_01_Core.md` | `WeightedAverageCostingMath.InventoryPool` |
| `WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException` | `DOC_01_Core.md` | `WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException` |
| `WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException` | `DOC_01_Core.md` | `WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException` |
| `CurrencyFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `DescriptorNamespaceSupport` | `DOC_02_MachineContractAndDescriptors.md` | `DescriptorNamespaceSupport` |
| `DiscoveryDetail` | `DOC_02_ProtocolAndDiscovery.md` | `DiscoveryDetail` |
| `EnvironmentDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentPublicationDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentRuntimeDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.FailedRuntime` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.IncompatibleRuntime` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.ReadyRuntime` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.RuntimeState` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.UnavailableRuntime` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `EnvironmentStorageDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ExecutionMode` | `DOC_02_ProtocolAndDiscovery.md` | `ExecutionMode` |
| `ExitCodeDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `HelpDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `MachineContract` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContract` |
| `MachineContractAttestationTemplates` | `DOC_02_MachineContractAndDescriptors.md` | `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates` |
| `MonetaryAmount` | `DOC_02_ProtocolAndDiscovery.md` | `MonetaryAmount` |
| `OperationCategory` | `DOC_02_ProtocolAndDiscovery.md` | `OperationCategory` |
| `OperationId` | `DOC_02_ProtocolAndDiscovery.md` | `OperationId` |
| `OutputMode` | `DOC_02_ProtocolAndDiscovery.md` | `OutputMode` |
| `PlanTemplateTopic` | `DOC_02_MachineContractAndDescriptors.md` | `PlanTemplateTopic` |
| `PreflightFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `ProtectedBookFormatContract` | `DOC_02_ProtocolAndDiscovery.md` | `ProtectedBookFormatContract` |
| `ProtocolArtifactOutput` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolArtifactOutput` |
| `ProtocolBookAccessOptions` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions` |
| `ProtocolCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolCatalog` |
| `ProtocolBusinessEventFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll` |
| `ProtocolBusinessEventFields.AccrualCutoff` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll` |
| `ProtocolBusinessEventFields.Core` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll` |
| `ProtocolBusinessEventFields.Financing` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll` |
| `ProtocolBusinessEventFields.FixedAsset` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll` |
| `ProtocolBusinessEventFields.Inventory` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll` |
| `ProtocolBusinessEventFields.LatvianPayroll` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll` |
| `ProtocolBusinessEventFields.RealizedForeignExchange` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll` |
| `ProtocolInteractionLimits` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolInteractionLimits` |
| `ProtocolCommandSignature` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolDeclareAccountFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolDeclareAccountFields.UnitOfMeasure` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolExampleStep` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolExampleStep` |
| `ProtocolEnvelopeStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload` And `ProtocolEnvelopeStatus` |
| `ProtocolLedgerPlanFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Assertion` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Plan` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Query` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Step` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolMoneyFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolMoneyFields` |
| `ProtocolOpenBookFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolOperation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOperationDocumentation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOperationOutputs` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOptionSyntax` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptionSyntax`, `ProtocolOptionSyntax.BookAccess`, `ProtocolOptionSyntax.Attestation`, `ProtocolOptionSyntax.ReportQuery`, `ProtocolOptionSyntax.Presentation`, And `ProtocolOptionSyntax.Discovery` |
| `ProtocolOptionSyntax.Attestation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptionSyntax`, `ProtocolOptionSyntax.BookAccess`, `ProtocolOptionSyntax.Attestation`, `ProtocolOptionSyntax.ReportQuery`, `ProtocolOptionSyntax.Presentation`, And `ProtocolOptionSyntax.Discovery` |
| `ProtocolOptionSyntax.BookAccess` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptionSyntax`, `ProtocolOptionSyntax.BookAccess`, `ProtocolOptionSyntax.Attestation`, `ProtocolOptionSyntax.ReportQuery`, `ProtocolOptionSyntax.Presentation`, And `ProtocolOptionSyntax.Discovery` |
| `ProtocolOptionSyntax.Discovery` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptionSyntax`, `ProtocolOptionSyntax.BookAccess`, `ProtocolOptionSyntax.Attestation`, `ProtocolOptionSyntax.ReportQuery`, `ProtocolOptionSyntax.Presentation`, And `ProtocolOptionSyntax.Discovery` |
| `ProtocolOptionSyntax.Presentation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptionSyntax`, `ProtocolOptionSyntax.BookAccess`, `ProtocolOptionSyntax.Attestation`, `ProtocolOptionSyntax.ReportQuery`, `ProtocolOptionSyntax.Presentation`, And `ProtocolOptionSyntax.Discovery` |
| `ProtocolOptionSyntax.ReportQuery` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptionSyntax`, `ProtocolOptionSyntax.BookAccess`, `ProtocolOptionSyntax.Attestation`, `ProtocolOptionSyntax.ReportQuery`, `ProtocolOptionSyntax.Presentation`, And `ProtocolOptionSyntax.Discovery` |
| `ProtocolOptions` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions` |
| `ProtocolOptions.Attestation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions.Request`, `ProtocolOptions.DateRange`, `ProtocolOptions.ReportQuery`, `ProtocolOptions.BookDefinition`, `ProtocolOptions.Attestation`, `ProtocolOptions.Presentation`, And `ProtocolOptions.Discovery` |
| `ProtocolOptions.BookDefinition` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions.Request`, `ProtocolOptions.DateRange`, `ProtocolOptions.ReportQuery`, `ProtocolOptions.BookDefinition`, `ProtocolOptions.Attestation`, `ProtocolOptions.Presentation`, And `ProtocolOptions.Discovery` |
| `ProtocolOptions.DateRange` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions.Request`, `ProtocolOptions.DateRange`, `ProtocolOptions.ReportQuery`, `ProtocolOptions.BookDefinition`, `ProtocolOptions.Attestation`, `ProtocolOptions.Presentation`, And `ProtocolOptions.Discovery` |
| `ProtocolOptions.Discovery` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions.Request`, `ProtocolOptions.DateRange`, `ProtocolOptions.ReportQuery`, `ProtocolOptions.BookDefinition`, `ProtocolOptions.Attestation`, `ProtocolOptions.Presentation`, And `ProtocolOptions.Discovery` |
| `ProtocolOptions.Presentation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions.Request`, `ProtocolOptions.DateRange`, `ProtocolOptions.ReportQuery`, `ProtocolOptions.BookDefinition`, `ProtocolOptions.Attestation`, `ProtocolOptions.Presentation`, And `ProtocolOptions.Discovery` |
| `ProtocolOptions.ReportQuery` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions.Request`, `ProtocolOptions.DateRange`, `ProtocolOptions.ReportQuery`, `ProtocolOptions.BookDefinition`, `ProtocolOptions.Attestation`, `ProtocolOptions.Presentation`, And `ProtocolOptions.Discovery` |
| `ProtocolOptions.Request` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions.Request`, `ProtocolOptions.DateRange`, `ProtocolOptions.ReportQuery`, `ProtocolOptions.BookDefinition`, `ProtocolOptions.Attestation`, `ProtocolOptions.Presentation`, And `ProtocolOptions.Discovery` |
| `ProtocolPostEntryFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Approval` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Evidence` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.InventoryRelief` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.JournalLine` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.OpeningBalance` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Provenance` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.RecognitionInterval` | `DOC_02_MachineContractAndDescriptors.md` | `ProtocolPostEntryFields.RecognitionInterval` |
| `ProtocolPostEntryFields.Reversal` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.SettlementAdjunct` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.SourceDocument` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Tax` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolBookRequestFieldSets` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, `ProtocolPostingNestedFieldSets`, And `ProtocolLedgerPlanRequestFieldSets` |
| `ProtocolPostingRequestFieldSets` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, `ProtocolPostingNestedFieldSets`, And `ProtocolLedgerPlanRequestFieldSets` |
| `ProtocolAccrualCutoffPostingRequestFieldSets` | `DOC_02_MachineContractAndDescriptors.md` | `ProtocolAccrualCutoffPostingRequestFieldSets` |
| `ProtocolInventoryPostingRequestFieldSets` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, `ProtocolPostingNestedFieldSets`, And `ProtocolLedgerPlanRequestFieldSets` |
| `ProtocolPostingNestedFieldSets` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, `ProtocolPostingNestedFieldSets`, And `ProtocolLedgerPlanRequestFieldSets` |
| `ProtocolPostingRequestTopics` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolPostingRequestTopics` |
| `ProtocolRequestTemplateTopics` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolRequestTemplateTopics` |
| `ProtocolLedgerPlanRequestFieldSets` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, `ProtocolPostingNestedFieldSets`, And `ProtocolLedgerPlanRequestFieldSets` |
| `ProtocolCapabilityBaselineSyncMain` | `DOC_02_MachineContractAndDescriptors.md` | `ProtocolCapabilityBaselineSyncMain` |
| `ProtocolEnvelopeCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolDomainCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolRuntimeCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolDistributionCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolManagedSqliteCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolSharedRequestFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSharedRequestFields` |
| `ProtocolSuccessPayload` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload` And `ProtocolEnvelopeStatus` |
| `ProtocolTaxRegistrationFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolTaxRegistrationFields.TaxCode` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `PublicCliBundleTarget` | `DOC_02_ProtocolAndDiscovery.md` | `PublicCliBundleTarget` |
| `PublicCliDistribution` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `PublicBundlePublicationStatus` | `DOC_02_ProtocolAndDiscovery.md` | `PublicBundlePublicationStatus` |
| `QuotedExchangeRate` | `DOC_02_ProtocolAndDiscovery.md` | `ForeignExchangeDetails`, `ForeignExchangeTreatmentKind`, And `QuotedExchangeRate` |
| `ReportCapabilityFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `RequestFieldPresence` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `RequestSurfaceFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.CommandTemporalScopeFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.EvidenceRequirementFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.BookkeepingEntryKindFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.ReachabilityCellFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.SourceDocumentTypeFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.TemporalScopeFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ResolvedJournal` | `DOC_02_PostingAndLedgerPlans.md` | `ResolvedJournal` |
| `PostEntryResolutionSupport` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure` |
| `PostEntryResolutionSupport.ResolutionOutcome` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure` |
| `PostingAccountStatePolicy` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure` |
| `QuantityText` | `DOC_02_PostingAndLedgerPlans.md` | `QuantityText`, `ResolvedInventoryAcquisition`, `ResolvedInventoryCosting`, And `ResolvedInventoryDisposal` |
| `ResolvedInventoryAcquisition` | `DOC_02_PostingAndLedgerPlans.md` | `QuantityText`, `ResolvedInventoryAcquisition`, `ResolvedInventoryCosting`, And `ResolvedInventoryDisposal` |
| `ResolvedInventoryCosting` | `DOC_02_PostingAndLedgerPlans.md` | `QuantityText`, `ResolvedInventoryAcquisition`, `ResolvedInventoryCosting`, And `ResolvedInventoryDisposal` |
| `ResolvedInventoryDisposal` | `DOC_02_PostingAndLedgerPlans.md` | `QuantityText`, `ResolvedInventoryAcquisition`, `ResolvedInventoryCosting`, And `ResolvedInventoryDisposal` |
| `RuntimeDistribution` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `ScaffoldPlaceholders` | `DOC_02_MachineContractAndDescriptors.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `SelectableOutputDefaultsDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `InventoryRelief` | `DOC_02_PostingAndLedgerPlans.md` | `InventoryRelief` |
| `SettlementAdjunct` | `DOC_02_PostingAndLedgerPlans.md` | `SettlementAdjunct` |
| `SqliteCompileOptionsVerificationStatus` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `SqliteLibraryMode` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeArtifactEvidence` | `DOC_02_ProtocolAndDiscovery.md` | `SqliteRuntimeArtifactEvidence` |
| `SqliteRuntimeProvenance` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeStateValidator` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeStatus` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeTrustBasis` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `StandardBookkeepingEntryVariants` | `DOC_02_PostingAndLedgerPlans.md` | `StandardBookkeepingEntryVariants` |
| `StorageDriver` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `StorageEngine` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `StorageSurfaceDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `StructuralContext` | `DOC_01_Core_LedgerAndPosting.md` | `StructuralContext` |
| `VersionDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `WorkflowDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowStepDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowStepKind` | `DOC_02_MachineContractAndDescriptors.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowSurface` | `DOC_02_MachineContractAndDescriptors.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `AccountBalanceQuery` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceQuery` |
| `AccountBalanceResult` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceResult` |
| `AccountBalanceSnapshot` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceSnapshot` |
| `AccountPage` | `DOC_02_AdministrationAndReports.md` | `AccountPage` |
| `AccountPageCursor` | `DOC_02_AdministrationAndReports.md` | `AccountPageCursor` |
| `BackupBookResult` | `DOC_02_AdministrationAndReports.md` | `BackupBookResult` |
| `BookAdministrationRejection` | `DOC_02_PeriodCloseAndRejections.md` | `BookAdministrationRejection` |
| `FiscalYearCloseRequiresGeneratedPostings` | `DOC_02_PeriodCloseAndRejections.md` | `BookAdministrationRejection` |
| `CloseTargetAccountCandidateMissing` | `DOC_02_PeriodCloseAndRejections.md` | `BookAdministrationRejection` |
| `CloseTargetAccountCandidateAmbiguous` | `DOC_02_PeriodCloseAndRejections.md` | `BookAdministrationRejection` |
| `BookAdministrationService` | `DOC_02_AdministrationAndReports.md` | `BookAdministrationService` |
| `BookInspection` | `DOC_02_AdministrationAndReports.md` | `BookInspection` |
| `BookTemplateAccounts` | `DOC_02_AdministrationAndReports.md` | `BookTemplateAccounts` |
| `BookMaintenanceArtifactRole` | `DOC_02_BookMaintenanceContracts.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, And `BookMaintenanceRejection` |
| `BookMaintenancePathFailure` | `DOC_02_BookMaintenanceContracts.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, And `BookMaintenanceRejection` |
| `BookMaintenanceRejection` | `DOC_02_BookMaintenanceContracts.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, And `BookMaintenanceRejection` |
| `BookMaintenanceVerificationFailure` | `DOC_02_BookMaintenanceContracts.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, And `BookMaintenanceRejection` |
| `BookMigrationPolicy` | `DOC_02_AdministrationAndReports.md` | `BookMigrationPolicy` |
| `BookMigrationPolicyMode` | `DOC_02_AdministrationAndReports.md` | `BookMigrationPolicy` |
| `BookQueryRejection` | `DOC_02_PeriodCloseAndRejections.md` | `BookQueryRejection` |
| `BookQueryReportResult` | `DOC_02_SharedReportModel.md` | `BookQueryReportResult` |
| `BookReadService` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `BookReadCatalogOperations` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `BookReadLifecycleOperations` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `BookReadPostingOperations` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `BookReadStatementOperations` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `BookkeepingReadPagePublishedLanguageTranslator` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `BookkeepingReadReportPublishedLanguageTranslator` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `BookkeepingReadStatementPublishedLanguageTranslator` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `ChangesInEquityCriteria` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityCriteria`, `ChangesInEquityRowView`, And `ChangesInEquityView` |
| `ChangesInEquityQuery` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult` |
| `ChangesInEquityReport` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult` |
| `ChangesInEquityResult` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult` |
| `ChangesInEquityRow` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult` |
| `ChangesInEquityRowView` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityCriteria`, `ChangesInEquityRowView`, And `ChangesInEquityView` |
| `ChangesInEquityView` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityCriteria`, `ChangesInEquityRowView`, And `ChangesInEquityView` |
| `AccountBalanceReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `AccountLedgerReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `CashFlowStatementReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `ChangesInEquityReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `FinancialPositionReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `IncomeStatementReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `PeriodSummaryReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `ReportColumn` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportColumn.Alignment` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportContext` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportCsvProjection` | `DOC_02_SharedReportModel.md` | `ReportCsvProjection` |
| `ReportModel` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportModel.Orientation` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportModelBuilder` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportRow` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportSection` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportTotals` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `ReportVerdict` | `DOC_02_SharedReportModel.md` | `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation` |
| `TaxObligationReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `TrialBalanceReportModelBuilder` | `DOC_02_SharedReportModel.md` | `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder` |
| `ClosedFiscalYear` | `DOC_02_AdministrationAndReports.md` | `InterimResultSweepCommand`, `InterimResultSweepResult`, `FiscalYearCloseCommand`, `FiscalYearCloseResult`, `SweptInterimResult`, And `ClosedFiscalYear` |
| `CloseTargetAccountSelector` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `CloseTargetSelection` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `DeclareAccountCommand` | `DOC_02_AdministrationAndReports.md` | `DeclareAccountCommand` |
| `DeclareAccountResult` | `DOC_02_AdministrationAndReports.md` | `DeclareAccountResult` |
| `DeclareTaxRegistrationCommand` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `DeclareTaxRegistrationResult` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `DeclaredAccount` | `DOC_02_AdministrationAndReports.md` | `DeclaredAccount` |
| `DeclaredTaxRegistration` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `FinancialPositionCriteria` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionQuery` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionReport` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionResult` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionRow` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionRowView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionSection` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionSectionView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FiscalYearCloseCommand` | `DOC_02_AdministrationAndReports.md` | `InterimResultSweepCommand`, `InterimResultSweepResult`, `FiscalYearCloseCommand`, `FiscalYearCloseResult`, `SweptInterimResult`, And `ClosedFiscalYear` |
| `FiscalYearCloseDraft` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `FiscalYearCloseOutcome` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `FiscalYearClosePlanner` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `FiscalYearCloseResult` | `DOC_02_AdministrationAndReports.md` | `InterimResultSweepCommand`, `InterimResultSweepResult`, `FiscalYearCloseCommand`, `FiscalYearCloseResult`, `SweptInterimResult`, And `ClosedFiscalYear` |
| `FiscalYearCloseService` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `GetPostingResult` | `DOC_02_AdministrationAndReports.md` | `GetPostingResult` |
| `IncomeStatementCriteria` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementGrossProfitSupport` | `DOC_02_IncomeStatementPresentation.md` | `IncomeStatementGrossProfitSupport` |
| `IncomeStatementPresentationSupport` | `DOC_02_IncomeStatementPresentation.md` | `IncomeStatementPresentationSupport`, `IncomeStatementPresentationSupport.SectionCode`, And `IncomeStatementPresentationSupport.PresentationSection` |
| `IncomeStatementPresentationSupport.PresentationSection` | `DOC_02_IncomeStatementPresentation.md` | `IncomeStatementPresentationSupport`, `IncomeStatementPresentationSupport.SectionCode`, And `IncomeStatementPresentationSupport.PresentationSection` |
| `IncomeStatementPresentationSupport.SectionCode` | `DOC_02_IncomeStatementPresentation.md` | `IncomeStatementPresentationSupport`, `IncomeStatementPresentationSupport.SectionCode`, And `IncomeStatementPresentationSupport.PresentationSection` |
| `IncomeStatementQuery` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementReport` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementResult` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementRow` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementRowView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementSection` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementSectionView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `InterimResultSweepCommand` | `DOC_02_AdministrationAndReports.md` | `InterimResultSweepCommand`, `InterimResultSweepResult`, `FiscalYearCloseCommand`, `FiscalYearCloseResult`, `SweptInterimResult`, And `ClosedFiscalYear` |
| `InterimResultSweepResult` | `DOC_02_AdministrationAndReports.md` | `InterimResultSweepCommand`, `InterimResultSweepResult`, `FiscalYearCloseCommand`, `FiscalYearCloseResult`, `SweptInterimResult`, And `ClosedFiscalYear` |
| `ListAccountsQuery` | `DOC_02_AdministrationAndReports.md` | `ListAccountsQuery` |
| `ListAccountsResult` | `DOC_02_AdministrationAndReports.md` | `ListAccountsResult` |
| `ListPostingsQuery` | `DOC_02_AdministrationAndReports.md` | `ListPostingsQuery` |
| `ListPostingsResult` | `DOC_02_AdministrationAndReports.md` | `ListPostingsResult` |
| `ListTaxRegistrationsQuery` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `ListTaxRegistrationsResult` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `OpenBookCommand` | `DOC_02_AdministrationAndReports.md` | `OpenBookCommand` |
| `OpenBookResult` | `DOC_02_AdministrationAndReports.md` | `OpenBookResult` |
| `PeriodAccountActivityRow` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `ClosedFiscalYearRecord` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `InterimResultSweepDraft` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `InterimResultSweepOutcome` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `InterimResultTargetSelection` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `AcceptedInterimResultTargetSelection` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `RejectedInterimResultTargetSelection` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `InterimResultSweepPlan` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `InterimResultSweepPlanner` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `InterimResultSweepService` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `PeriodCurrencySummary` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryQuery` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryReport` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryResult` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PostingPage` | `DOC_02_AdministrationAndReports.md` | `PostingPage` |
| `PostingPageCursor` | `DOC_02_AdministrationAndReports.md` | `PostingPageCursor` |
| `PublicPathHint` | `DOC_02_BookMaintenanceContracts.md` | `PublicPathHint` |
| `RejectionNarrative` | `DOC_02_BookMaintenanceContracts.md` | `RejectionNarrative` |
| `RekeyBookResult` | `DOC_02_AdministrationAndReports.md` | `RekeyBookResult` |
| `RestoreBookResult` | `DOC_02_AdministrationAndReports.md` | `RestoreBookResult` |
| `TaxAdministrationService` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxAdministrationStore` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxApplicationKind` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxCode` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxCodeDefinition` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxCodeName` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxDeclarationRejection` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxDefinitionViolation` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxInclusionMode` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxJurisdiction` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxObligationCodeSummary` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxObligationFrequency` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxObligationQuery` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxObligationReport` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxObligationResult` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxQueryRejection` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxRate` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxReadService` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxReadStore` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxRegistrationCatalogStore` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxRegistrationId` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxRegistrationLookupStore` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxRegistrationName` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxRegistrationNumber` | `DOC_02_AdministrationAndReports.md` | `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore` |
| `TaxRegistrationPage` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxRegistrationPageCursor` | `DOC_02_AdministrationAndReports.md` | `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore` |
| `TaxSelection` | `DOC_02_PostingAndLedgerPlans.md` | `TaxSelection` And `AppliedTax` |
| `TrialBalanceQuery` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceReport` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceResult` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceRow` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `PostingRangeStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `ReportingPeriodCloseStore`, And `LedgerPlanExecutionStore` |
| `PostingValidationStore` | `DOC_03_BookSessionsAndAdapters.md` | `PostingValidationStore` |
| `RegisteredAccount` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `StoredRequestPosting` | `DOC_03_BookSessionsAndAdapters.md` | `PostingDraft`, `PostingCommitResult`, `PostingIdGenerator`, And `StoredRequestPosting` |
| `SweptInterimResult` | `DOC_02_AdministrationAndReports.md` | `InterimResultSweepCommand`, `InterimResultSweepResult`, `FiscalYearCloseCommand`, `FiscalYearCloseResult`, `SweptInterimResult`, And `ClosedFiscalYear` |
| `RecordedInterimResultSweep` | `DOC_02_PeriodCloseAndRejections.md` | `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService` |
| `AcceptedCloseTargetSelection` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `RejectedCloseTargetSelection` | `DOC_02_PeriodCloseAndRejections.md` | `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService` |
| `TrialBalanceCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `TrialBalanceRowView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `TrialBalanceView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `UnsupportedManagedSqliteLibraryIdentityException` | `DOC_03_SqliteRuntimeAndSessions.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, `SqliteProtectedBookVerificationException`, `SqliteStorageFailureException`, And `SqliteOpenBookCompletionUncertainException` |
| `UnsupportedSqliteCompileOptionsException` | `DOC_03_SqliteRuntimeAndSessions.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, `SqliteProtectedBookVerificationException`, `SqliteStorageFailureException`, And `SqliteOpenBookCompletionUncertainException` |
| `SqliteProtectedBookVerificationException` | `DOC_03_SqliteRuntimeAndSessions.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, `SqliteProtectedBookVerificationException`, `SqliteStorageFailureException`, And `SqliteOpenBookCompletionUncertainException` |
| `SqliteOpenBookCompletionUncertainException` | `DOC_03_SqliteRuntimeAndSessions.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, `SqliteProtectedBookVerificationException`, `SqliteStorageFailureException`, And `SqliteOpenBookCompletionUncertainException` |
| `App` | `DOC_04_CliAndPdfAdapters.md` | `App` |
| `PdfReportService` | `DOC_04_CliAndPdfAdapters.md` | `PdfReportService` |
