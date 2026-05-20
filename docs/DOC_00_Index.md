---
afad: "4.0"
version: "0.43.0"
domain: INDEX
updated: "2026-05-20"
route:
  keywords: [fingrind, index, routing, api, symbols, core, contract, executor, sqlite, cli, report-pdf, machine-contract, book-session]
  questions: ["where is the fingrind api documented", "which doc file covers SqliteBookSessions", "which doc file covers RequestProvenance", "which doc file covers ProtocolCatalog", "which doc file covers PdfReportService"]
---

# API Index

**Purpose**: Route readers first by bounded context, then by file and symbol.

## Context Routing

- Public bookkeeping protocol:
  use [DOC_02_AdministrationAndReports.md](./DOC_02_AdministrationAndReports.md) for
  administration, inspection, queries, reports, and read-side rejections; use
  [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md) for posting commands,
  posting results, and write-side rejections.
- Public workflow protocol:
  use [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md) for `LedgerPlan`,
  `LedgerStep`, `LedgerAssertion`, and the public `LedgerJournal*` / `LedgerPlanResult` surface.
- Runtime/discovery contract:
  use [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md) for machine-contract
  descriptors, runtime/distribution/storage facts, discovery metadata, and templates.
- Decimal-boundary design:
  use [DOC_01_DecimalBoundaries.md](./DOC_01_DecimalBoundaries.md) for the exact-money boundary
  and the future split between money, rates, percentages, exchange rates, and other decimal
  factors.
- Accounting baseline and current standards scope:
  use [ADR_ACCOUNTING_BASELINE.md](./ADR_ACCOUNTING_BASELINE.md) for the named country-agnostic
  bookkeeping baseline, standards references, and intentional exclusions.
- Local bookkeeping context:
  use [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md) for executor-owned
  sessions plus local bookkeeping read/write models that cross those seams.
- Local workflow context:
  use [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md) for local workflow
  plans, steps, assertions, internal workflow journals, and the published-language translator that
  projects them outward.
- Adapter/runtime seams:
  use [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md) for SQLite-backed
  session/runtime types and [DOC_04_CliAndPdfAdapters.md](./DOC_04_CliAndPdfAdapters.md) for CLI
  and PDF adapter entrypoints.

## File Index

| File | Scope |
|:-----|:------|
| `DOC_01_Core.md` | exported accounting vocabulary and invariants from the `core` module |
| `DOC_01_DecimalBoundaries.md` | exact-money boundary and future non-money decimal-domain rules |
| `DOC_02_Application.md` | routing overview for the split `contract` and `executor` reference spine |
| `DOC_02_ProtocolAndDiscovery.md` | exported `contract` protocol metadata, discovery namespaces, request/response descriptors, deterministic contract errors, and workflow/discovery owners |
| `DOC_02_AdministrationAndReports.md` | exported administration/query/report models and exported `executor` administration and read services |
| `DOC_02_PostingAndLedgerPlans.md` | exported posting, rejection, lineage, ledger-plan, and plan-journal models plus exported `executor` write services |
| `DOC_03_BookSessionsAndAdapters.md` | explicit book-access tuples, committed facts, executor-owned seams, and exported SQLite adapter/runtime types |
| `DOC_04_CliAndPdfAdapters.md` | public CLI process entrypoint and exported PDF-report adapter |

## Symbol Routing

| Symbol | File | Section |
|:-------|:-----|:--------|
| `AccountCode` | `DOC_01_Core.md` | `AccountCode` |
| `AccountCodePolicy` | `DOC_01_Core.md` | `AccountCodePolicy` |
| `AccountCodePolicy.Meaning` | `DOC_01_Core.md` | `AccountCodePolicy.Meaning` |
| `AccountCodePolicy.ChartStructure` | `DOC_01_Core.md` | `AccountCodePolicy.ChartStructure` |
| `AccountName` | `DOC_01_Core.md` | `AccountName` |
| `BookEntityName` | `DOC_01_Core.md` | `BookEntityName` |
| `BusinessActivityTag` | `DOC_01_Core.md` | `BusinessActivityTag` |
| `EntityForm` | `DOC_01_Core.md` | `EntityForm` |
| `OwnerModel` | `DOC_01_Core.md` | `OwnerModel` |
| `ReportingObligationStatus` | `DOC_01_Core.md` | `ReportingObligationStatus` |
| `AccountingBasis` | `DOC_01_Core.md` | `AccountingBasis` |
| `AccountingEvidence` | `DOC_01_Core.md` | `AccountingEvidence` |
| `ApprovalId` | `DOC_01_Core.md` | `ApprovalId` |
| `ApprovalReference` | `DOC_01_Core.md` | `ApprovalReference` |
| `ApprovalType` | `DOC_01_Core.md` | `ApprovalType` |
| `EntityProfile` | `DOC_01_Core.md` | `EntityProfile` |
| `BookIdentity` | `DOC_01_Core.md` | `BookIdentity` |
| `AccountType` | `DOC_01_Core.md` | `AccountType` |
| `AccountRole` | `DOC_01_Core.md` | `AccountRole` |
| `AccountTaxonomy` | `DOC_01_Core.md` | `AccountTaxonomy` |
| `FinancialPositionLineClassification` | `DOC_01_Core.md` | `FinancialPositionLineClassification` |
| `ProfitAndLossLineClassification` | `DOC_01_Core.md` | `ProfitAndLossLineClassification` |
| `StatementLineKind` | `DOC_01_Core.md` | `StatementLineKind` |
| `AccountSemantics` | `DOC_01_Core.md` | `AccountSemantics` |
| `ActorId` | `DOC_01_Core.md` | `ActorId` |
| `ActorType` | `DOC_01_Core.md` | `ActorType` |
| `BalanceMath` | `DOC_01_Core.md` | `BalanceMath` |
| `BalanceSide` | `DOC_01_Core.md` | `BalanceSide` |
| `CausationId` | `DOC_01_Core.md` | `CausationId` |
| `CommandId` | `DOC_01_Core.md` | `CommandId` |
| `CommittedProvenance` | `DOC_01_Core.md` | `CommittedProvenance` |
| `CorrelationId` | `DOC_01_Core.md` | `CorrelationId` |
| `CurrencyUnit` | `DOC_01_Core.md` | `CurrencyUnit` |
| `FiscalYearStart` | `DOC_01_Core.md` | `FiscalYearStart` |
| `IdempotencyKey` | `DOC_01_Core.md` | `IdempotencyKey` |
| `JournalEntry` | `DOC_01_Core.md` | `JournalEntry` |
| `JournalEntryValidationException` | `DOC_01_Core.md` | `JournalEntryValidationException` |
| `JournalLine` | `DOC_01_Core.md` | `JournalLine` |
| `JournalLine.EntrySide` | `DOC_01_Core.md` | `JournalLine.EntrySide` |
| `Money` | `DOC_01_Core.md` | `Money` |
| `PositiveMoney` | `DOC_01_Core.md` | `PositiveMoney` |
| `NormalBalance` | `DOC_01_Core.md` | `NormalBalance` |
| `PostingKind` | `DOC_01_Core.md` | `PostingKind` |
| `PostingCoverage` | `DOC_01_Core.md` | `PostingCoverage` |
| `PostingId` | `DOC_01_Core.md` | `PostingId` |
| `ReportingPeriod` | `DOC_01_Core.md` | `ReportingPeriod` |
| `RequestProvenance` | `DOC_01_Core.md` | `RequestProvenance` |
| `SourceDocumentId` | `DOC_01_Core.md` | `SourceDocumentId` |
| `SourceDocumentReference` | `DOC_01_Core.md` | `SourceDocumentReference` |
| `SourceDocumentType` | `DOC_01_Core.md` | `SourceDocumentType` |
| `ReversalReason` | `DOC_01_Core.md` | `ReversalReason` |
| `ReversalReference` | `DOC_01_Core.md` | `ReversalReference` |
| `SourceChannel` | `DOC_01_Core.md` | `SourceChannel` |
| `WireValue` | `DOC_01_Core.md` | `WireValue` |
| `ProtocolCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolCatalog` |
| `ProtocolOperation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolCommandSignature` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOperationOutputs` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOperationDocumentation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolExampleStep` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolExampleStep` |
| `OperationId` | `DOC_02_ProtocolAndDiscovery.md` | `OperationId` |
| `OperationCategory` | `DOC_02_ProtocolAndDiscovery.md` | `OperationCategory` |
| `ExecutionMode` | `DOC_02_ProtocolAndDiscovery.md` | `ExecutionMode` |
| `OutputMode` | `DOC_02_ProtocolAndDiscovery.md` | `OutputMode` |
| `PlanResultDetail` | `DOC_02_ProtocolAndDiscovery.md` | `PlanResultDetail` |
| `ProtocolSuccessStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload`, `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, `ProtocolFailureStatus`, And `ProtocolDiagnosticCode` |
| `ProtocolRejectionStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload`, `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, `ProtocolFailureStatus`, And `ProtocolDiagnosticCode` |
| `ProtocolFailureStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload`, `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, `ProtocolFailureStatus`, And `ProtocolDiagnosticCode` |
| `ProtocolDiagnosticCode` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload`, `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, `ProtocolFailureStatus`, And `ProtocolDiagnosticCode` |
| `ProtocolSuccessPayload` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload`, `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, `ProtocolFailureStatus`, And `ProtocolDiagnosticCode` |
| `InteractionLimits` | `DOC_01_Core.md` | `InteractionLimits` |
| `ProtocolOptions` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions` |
| `ProtocolArtifactOutput` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolArtifactOutput` |
| `PublicDistributionContract` | `DOC_02_ProtocolAndDiscovery.md` | `PublicDistributionContract` |
| `PublicCliBundleTarget` | `DOC_02_ProtocolAndDiscovery.md` | `PublicCliBundleTarget` |
| `PlanTransactionMode` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `PlanFailurePolicy` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `RuntimeDistribution` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `PublicCliDistribution` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `StorageDriver` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `StorageEngine` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `BookProtectionMode` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `BookCipher` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteLibraryMode` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeProvenance` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeTrustBasis` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeStatus` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeStateValidator` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `BookModelFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookBoundaryFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookEntityScopeFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookFilesystemFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookCredentialFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookInitializationFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookAccountRegistryFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookCurrencyScopeFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `CurrencyFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `AccountingBaselineFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `AccountingBaselineTarget` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `ReportCapabilityFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `AccountingPolicyPackFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PolicyDimensionFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `ExtensionSurfaceFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PolicySeamFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `CapabilityStatus` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PreflightFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PlanExecutionFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts` |
| `MonetaryAmount` | `DOC_02_ProtocolAndDiscovery.md` | `MonetaryAmount` |
| `ProtocolSharedRequestFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSharedRequestFields` |
| `ProtocolMoneyFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolMoneyFields` |
| `ProtocolOpenBookFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolDeclareAccountFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.TopLevel` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Evidence` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.SourceDocument` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Approval` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.JournalLine` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Provenance` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Reversal` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Plan` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Step` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Query` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Assertion` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ScaffoldPlaceholders` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `MachineContract` | `DOC_02_ProtocolAndDiscovery.md` | `MachineContract` |
| `ContractDiscovery` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscoveryDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ApplicationIdentity` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `HelpDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `WorkflowSurface` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowStepKind` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowStepDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `CapabilitiesDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `StorageSurfaceDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `CommandCatalogDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `VersionDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ArtifactOutputDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `CommandDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `SelectableOutputDefaultsDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ExitCodeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentDistributionDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentStorageDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.RuntimeState` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.ReadyRuntime` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.UnavailableRuntime` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.FailedRuntime` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.IncompatibleRuntime` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `SqliteCompileOptionsVerificationStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestShapeDescriptorType` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestInputDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestShapesDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.PostEntryRequestShapeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.DeclareAccountRequestShapeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.LedgerPlanRequestShapeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestFieldDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `RequestFieldPresence` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.EnumVocabularyDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ResponseDescriptorType` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.BookModelDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.FieldDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ErrorDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ResponseModelDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.PlanExecutionDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.RejectionDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AuditDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AccountRegistryDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.InitializationRequirement` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ReversalDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.PreflightDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.CommitGuarantee` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.CurrencyDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AccountingBaselineDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ExtensionSurfaceDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.TemplateDescriptorType` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.OpenBookTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.PostingRequestTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.AccountingEvidenceTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.SourceDocumentTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.ApprovalTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.JournalLineTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.ProvenanceTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.ReversalTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.LedgerPlanTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.LedgerPlanStepTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.LedgerPlanQueryTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.DeclareAccountTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.LedgerAssertionTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractErrors` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `ContractErrors.Descriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailure` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `ContractDecision` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailureException` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `DescriptorNamespaceSupport` | `DOC_02_ProtocolAndDiscovery.md` | `DescriptorNamespaceSupport` |
| `BookFormatContract` | `DOC_02_ProtocolAndDiscovery.md` | `BookFormatContract` |
| `ProtectedBookFormatContract` | `DOC_02_ProtocolAndDiscovery.md` | `ProtectedBookFormatContract` |
| `SqliteRuntimeArtifactEvidence` | `DOC_02_ProtocolAndDiscovery.md` | `SqliteRuntimeArtifactEvidence` |
| `BookAdministrationService` | `DOC_02_AdministrationAndReports.md` | `BookAdministrationService` |
| `BookReadService` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `DeclareAccountCommand` | `DOC_02_AdministrationAndReports.md` | `DeclareAccountCommand` |
| `DeclaredAccount` | `DOC_02_AdministrationAndReports.md` | `DeclaredAccount` |
| `ClosePeriodCommand` | `DOC_02_AdministrationAndReports.md` | `ClosePeriodCommand`, `ClosePeriodResult`, And `ClosedPeriod` |
| `ClosePeriodResult` | `DOC_02_AdministrationAndReports.md` | `ClosePeriodCommand`, `ClosePeriodResult`, And `ClosedPeriod` |
| `ClosedPeriod` | `DOC_02_AdministrationAndReports.md` | `ClosePeriodCommand`, `ClosePeriodResult`, And `ClosedPeriod` |
| `OpenBookCommand` | `DOC_02_AdministrationAndReports.md` | `OpenBookCommand` |
| `OpenBookResult` | `DOC_02_AdministrationAndReports.md` | `OpenBookResult` |
| `DeclareAccountResult` | `DOC_02_AdministrationAndReports.md` | `DeclareAccountResult` |
| `RekeyBookResult` | `DOC_02_AdministrationAndReports.md` | `RekeyBookResult` |
| `BackupBookResult` | `DOC_02_AdministrationAndReports.md` | `BackupBookResult` |
| `RestoreBookResult` | `DOC_02_AdministrationAndReports.md` | `RestoreBookResult` |
| `RekeyRollbackResult` | `DOC_02_AdministrationAndReports.md` | `RekeyRollbackResult` |
| `BookMaintenanceArtifactRole` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceArtifactRole`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint` |
| `BookMaintenanceVerificationFailure` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceArtifactRole`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint` |
| `PublicPathHint` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceArtifactRole`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint` |
| `BookInspection` | `DOC_02_AdministrationAndReports.md` | `BookInspection` |
| `BookMigrationPolicy` | `DOC_02_AdministrationAndReports.md` | `BookMigrationPolicy` |
| `BookMigrationPolicyMode` | `DOC_02_AdministrationAndReports.md` | `BookMigrationPolicy` |
| `ListAccountsQuery` | `DOC_02_AdministrationAndReports.md` | `ListAccountsQuery` |
| `AccountPageCursor` | `DOC_02_AdministrationAndReports.md` | `AccountPageCursor` |
| `AccountPage` | `DOC_02_AdministrationAndReports.md` | `AccountPage` |
| `ListAccountsResult` | `DOC_02_AdministrationAndReports.md` | `ListAccountsResult` |
| `GetPostingResult` | `DOC_02_AdministrationAndReports.md` | `GetPostingResult` |
| `EffectiveDateRange` | `DOC_01_Core.md` | `EffectiveDateRange` |
| `PostingPageCursor` | `DOC_02_AdministrationAndReports.md` | `PostingPageCursor` |
| `ListPostingsQuery` | `DOC_02_AdministrationAndReports.md` | `ListPostingsQuery` |
| `PostingPage` | `DOC_02_AdministrationAndReports.md` | `PostingPage` |
| `ListPostingsResult` | `DOC_02_AdministrationAndReports.md` | `ListPostingsResult` |
| `AccountBalanceQuery` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceQuery` |
| `CurrencyBalance` | `DOC_01_Core.md` | `CurrencyBalance` |
| `AccountBalanceSnapshot` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceSnapshot` |
| `AccountBalanceResult` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceResult` |
| `TrialBalanceQuery` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceRow` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceReport` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceResult` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `AccountLedgerQuery` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerEntry` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerReport` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerResult` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `PeriodSummaryQuery` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodCurrencySummary` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodAccountActivityRow` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryReport` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryResult` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `FinancialPositionQuery` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionRow` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionSection` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionReport` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionResult` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `IncomeStatementQuery` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementRow` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementSection` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementReport` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementResult` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `ChangesInEquityQuery` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult` |
| `ChangesInEquityRow` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult` |
| `ChangesInEquityReport` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult` |
| `ChangesInEquityResult` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult` |
| `FinancialPositionCriteria` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionRowView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionSectionView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `IncomeStatementCriteria` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementRowView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementSectionView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `ChangesInEquityCriteria` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityCriteria`, `ChangesInEquityRowView`, And `ChangesInEquityView` |
| `ChangesInEquityRowView` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityCriteria`, `ChangesInEquityRowView`, And `ChangesInEquityView` |
| `ChangesInEquityView` | `DOC_02_AdministrationAndReports.md` | `ChangesInEquityCriteria`, `ChangesInEquityRowView`, And `ChangesInEquityView` |
| `PeriodCloseDraft` | `DOC_02_AdministrationAndReports.md` | `PeriodCloseDraft`, `PeriodCloseOutcome`, `PeriodClosePlanner`, And `PeriodCloseService` |
| `PeriodCloseOutcome` | `DOC_02_AdministrationAndReports.md` | `PeriodCloseDraft`, `PeriodCloseOutcome`, `PeriodClosePlanner`, And `PeriodCloseService` |
| `PeriodClosePlanner.ClosingEquitySelection` | `DOC_02_AdministrationAndReports.md` | `PeriodCloseDraft`, `PeriodCloseOutcome`, `PeriodClosePlanner`, And `PeriodCloseService` |
| `PeriodClosePlanner.AcceptedClosingEquitySelection` | `DOC_02_AdministrationAndReports.md` | `PeriodCloseDraft`, `PeriodCloseOutcome`, `PeriodClosePlanner`, And `PeriodCloseService` |
| `PeriodClosePlanner.RejectedClosingEquitySelection` | `DOC_02_AdministrationAndReports.md` | `PeriodCloseDraft`, `PeriodCloseOutcome`, `PeriodClosePlanner`, And `PeriodCloseService` |
| `PeriodClosePlanner.PeriodClosePlan` | `DOC_02_AdministrationAndReports.md` | `PeriodCloseDraft`, `PeriodCloseOutcome`, `PeriodClosePlanner`, And `PeriodCloseService` |
| `PeriodClosePlanner` | `DOC_02_AdministrationAndReports.md` | `PeriodCloseDraft`, `PeriodCloseOutcome`, `PeriodClosePlanner`, And `PeriodCloseService` |
| `PeriodCloseService` | `DOC_02_AdministrationAndReports.md` | `PeriodCloseDraft`, `PeriodCloseOutcome`, `PeriodClosePlanner`, And `PeriodCloseService` |
| `BookAdministrationRejection` | `DOC_02_AdministrationAndReports.md` | `BookAdministrationRejection` |
| `BookQueryRejection` | `DOC_02_AdministrationAndReports.md` | `BookQueryRejection` |
| `RejectionNarrative` | `DOC_02_AdministrationAndReports.md` | `RejectionNarrative` |
| `PostingLineage` | `DOC_02_PostingAndLedgerPlans.md` | `PostingLineage` |
| `PostEntryCommand` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryCommand` |
| `PostEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PreflightEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `CommitEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PostingRequest` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRequest` |
| `LedgerPlanId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerStepId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerPlan` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlan` |
| `LedgerStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStep` |
| `LedgerAssertion` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerAssertion` |
| `LedgerFact` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerFact` |
| `LedgerStepKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerJournalKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerAssertionKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerBoundaryPhase` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerStepStatus` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerPlanStatus` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerJournalStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerJournalEntry` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerExecutionJournal` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerStepFailure` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerPlanResult` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `PostingApplicationService` | `DOC_02_PostingAndLedgerPlans.md` | `PostingApplicationService` |
| `LedgerPlanService` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanService` |
| `UuidV7PostingIdGenerator` | `DOC_02_PostingAndLedgerPlans.md` | `UuidV7PostingIdGenerator` |
| `PostingRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection` |
| `PostingCommand` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PostingLineageModel` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PostingRequestModel` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PostingAcceptancePolicy` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingAdministrationRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingPostingRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingPublishedLanguageTranslator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator` |
| `CommittedPosting` | `DOC_03_BookSessionsAndAdapters.md` | `CommittedPosting` |
| `AccountDeclaration` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `AccountDeclarationOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `BookAuditEvent` | `DOC_03_BookSessionsAndAdapters.md` | `BookAuditEvent` And `BookAuditEventKind` |
| `BookAuditEventKind` | `DOC_03_BookSessionsAndAdapters.md` | `BookAuditEvent` And `BookAuditEventKind` |
| `AccountCurrencyTotals` | `DOC_03_BookSessionsAndAdapters.md` | `AccountCurrencyTotals` |
| `BookOpeningOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `RegisteredAccount` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `BookLifecycleReader` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `BookAdministrationStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `AccountLookupStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `AccountCatalogStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `PostingLookupStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `PostingHistoryStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `PostingRangeStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `BookkeepingReportStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `BookkeepingReadStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `PostingValidationStore` | `DOC_03_BookSessionsAndAdapters.md` | `PostingValidationStore` |
| `PostingCommitStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `PeriodCloseStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `LedgerPlanTransaction` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodCloseStore`, And `LedgerPlanTransaction` |
| `BookLifecycleInspection` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleInspection` And `BookInspectionPublishedLanguageTranslator` |
| `BookInspectionPublishedLanguageTranslator` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleInspection` And `BookInspectionPublishedLanguageTranslator` |
| `BookkeepingQueryRejection` | `DOC_03_BookSessionsAndAdapters.md` | `BookkeepingQueryRejection` |
| `BookkeepingReadPublishedLanguageTranslator` | `DOC_03_BookSessionsAndAdapters.md` | `BookkeepingQueryRejection` |
| `BookMaintenanceRejection` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceRejection` |
| `ProtectedBookAccess` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookAccess` |
| `MaintenanceDecision` | `DOC_03_BookSessionsAndAdapters.md` | `MaintenanceDecision`, `MaintenanceCompletion`, And `MaintenanceFailure` |
| `MaintenanceCompletion` | `DOC_03_BookSessionsAndAdapters.md` | `MaintenanceDecision`, `MaintenanceCompletion`, And `MaintenanceFailure` |
| `MaintenanceFailure` | `DOC_03_BookSessionsAndAdapters.md` | `MaintenanceDecision`, `MaintenanceCompletion`, And `MaintenanceFailure` |
| `ProtectedBookBackupOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookBackupOutcome`, `ProtectedBookRestoreOutcome`, And `ProtectedBookRecoveryOutcome` |
| `ProtectedBookRestoreOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookBackupOutcome`, `ProtectedBookRestoreOutcome`, And `ProtectedBookRecoveryOutcome` |
| `ProtectedBookRecoveryOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookBackupOutcome`, `ProtectedBookRestoreOutcome`, And `ProtectedBookRecoveryOutcome` |
| `ProtectedBookMaintenanceArtifactRole` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenanceRejection`, And `ProtectedBookMaintenanceWorkflow` |
| `ProtectedBookMaintenanceRejection` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenanceRejection`, And `ProtectedBookMaintenanceWorkflow` |
| `ProtectedBookPassphraseSource` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookAccess` And `ProtectedBookPassphraseSource` |
| `ProtectedBookMaintenanceWorkflow` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenanceRejection`, And `ProtectedBookMaintenanceWorkflow` |
| `AccountRegistryCursor` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountRegistryQuery` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountRegistryPage` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PostingHistoryCursor` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PostingHistoryQuery` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PostingHistoryPage` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountBalanceCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountBalanceView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `TrialBalanceCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `TrialBalanceRowView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `TrialBalanceView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountLedgerCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountLedgerEntryView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountLedgerView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PeriodSummaryCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PeriodCurrencySummaryView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PeriodAccountActivityView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PeriodSummaryView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PostingDraft` | `DOC_03_BookSessionsAndAdapters.md` | `PostingDraft`, `PostingCommitResult`, And `PostingIdGenerator` |
| `PostingCommitResult` | `DOC_03_BookSessionsAndAdapters.md` | `PostingDraft`, `PostingCommitResult`, And `PostingIdGenerator` |
| `PostingIdGenerator` | `DOC_03_BookSessionsAndAdapters.md` | `PostingDraft`, `PostingCommitResult`, And `PostingIdGenerator` |
| `BookAccess` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` And `BookAccess.PassphraseSource` |
| `BookAccess.PassphraseSource` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` And `BookAccess.PassphraseSource` |
| `PostingFact` | `DOC_03_BookSessionsAndAdapters.md` | `PostingFact` |
| `SqliteBookPassphrase` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookPassphrase` |
| `SqliteBookKeyFile` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookKeyFile`, `SqliteBookKeyFileGenerator`, And `SqliteBookKeyFileGenerator.GeneratedKeyFile` |
| `SqliteBookKeyFileGenerator` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookKeyFile`, `SqliteBookKeyFileGenerator`, And `SqliteBookKeyFileGenerator.GeneratedKeyFile` |
| `SqliteBookKeyFileGenerator.GeneratedKeyFile` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookKeyFile`, `SqliteBookKeyFileGenerator`, And `SqliteBookKeyFileGenerator.GeneratedKeyFile` |
| `SqliteRuntime` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteRuntime.Probe` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteRuntime.Status` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteFailureClassifier` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteFailureClassifier` And `SqliteFailureClassifier.Category` |
| `SqliteFailureClassifier.Category` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteFailureClassifier` And `SqliteFailureClassifier.Category` |
| `ManagedSqliteRuntimeUnavailableException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException` |
| `UnsupportedManagedSqliteLibraryIdentityException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException` |
| `UnsupportedSqliteCompileOptionsException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException` |
| `SqliteStorageFailureException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException` |
| `SqliteAdministrationSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteReadSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePostingSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePeriodCloseSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePlanExecutionSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteRekeySession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteBookSessionMode` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePassphraseIntent` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePassphraseResolver` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteBookSessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `ProtectedBookMaintenanceAuditKind` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceAuditKind` |
| `ProtectedBookMaintenanceAuditCompensationKind` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceAuditCompensationKind` |
| `ProtectedBookMaintenanceService` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, And `SqliteProtectedBookMaintenanceStore` |
| `ProtectedBookMaintenancePublishedLanguageTranslator` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenancePublishedLanguageTranslator` And `ProtectedBookVerificationFailure` |
| `ProtectedBookVerificationFailure` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenancePublishedLanguageTranslator` And `ProtectedBookVerificationFailure` |
| `ProtectedBookMaintenanceStore` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, And `SqliteProtectedBookMaintenanceStore` |
| `SqliteProtectedBookMaintenanceStore` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, And `SqliteProtectedBookMaintenanceStore` |
| `ChartOfAccounts` | `DOC_03_BookSessionsAndAdapters.md` | `ChartOfAccounts` |
| `App` | `DOC_04_CliAndPdfAdapters.md` | `App` |
| `PdfReportService` | `DOC_04_CliAndPdfAdapters.md` | `PdfReportService` |
