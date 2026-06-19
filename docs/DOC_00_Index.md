---
afad: "4.0"
version: "0.57.0"
domain: INDEX
updated: "2026-06-19"
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
- Bookkeeping-kernel scope and intentional exclusions:
  use [ADR_ACCOUNTING_KERNEL_SCOPE.md](./ADR_ACCOUNTING_KERNEL_SCOPE.md) for the current
  narrow built-in bookkeeping kernel, its public truth boundaries, and its intentional
  exclusions.
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
| `DOC_01_Core_EvidenceAndWire.md` | exported source-document evidence primitives, reversal lineage, source-channel provenance, and shared wire-vocabulary support from the `core` module |
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
| `AccountCodePolicy.ChartStructure` | `DOC_01_Core.md` | `AccountCodePolicy.ChartStructure` |
| `AccountCodePolicy.Meaning` | `DOC_01_Core.md` | `AccountCodePolicy.Meaning` |
| `AccountName` | `DOC_01_Core.md` | `AccountName` |
| `AccountNodeKind` | `DOC_01_Core.md` | `AccountNodeKind` |
| `AccountRole` | `DOC_01_Core.md` | `AccountRole` |
| `AccountSemantics` | `DOC_01_Core.md` | `AccountSemantics` |
| `AccountClassificationReachability` | `DOC_01_Core.md` | `AccountClassificationReachability` |
| `AccountClassificationReachability.ReachabilityCell` | `DOC_01_Core.md` | `AccountClassificationReachability.ReachabilityCell` |
| `AccountTaxonomy` | `DOC_01_Core.md` | `AccountTaxonomy` |
| `AccountType` | `DOC_01_Core.md` | `AccountType` |
| `AccountingBasis` | `DOC_01_Core.md` | `AccountingBasis` |
| `AccountingEvidence` | `DOC_01_Core.md` | `AccountingEvidence` |
| `AccountingFrameworkPosition` | `DOC_01_Core.md` | `AccountingFrameworkPosition` |
| `AccountingKernelProfileId` | `DOC_01_Core.md` | `AccountingKernelProfileId` |
| `AccountingKernelProfiles` | `DOC_01_Core.md` | `AccountingKernelProfiles` |
| `ActorId` | `DOC_01_Core.md` | `ActorId` |
| `ActorType` | `DOC_01_Core.md` | `ActorType` |
| `ApprovalDecision` | `DOC_01_Core.md` | `ApprovalDecision` |
| `ApprovalId` | `DOC_01_Core.md` | `ApprovalId` |
| `ApprovalReference` | `DOC_01_Core.md` | `ApprovalReference` |
| `ApprovalType` | `DOC_01_Core.md` | `ApprovalType` |
| `BalanceMath` | `DOC_01_Core.md` | `BalanceMath` |
| `BalanceSide` | `DOC_01_Core.md` | `BalanceSide` |
| `BookDoctrine` | `DOC_01_Core.md` | `BookDoctrine` |
| `BookDoctrineDisplay` | `DOC_01_Core.md` | `BookDoctrineDisplay` |
| `BookDoctrines` | `DOC_01_Core.md` | `BookDoctrines` |
| `BookEntityName` | `DOC_01_Core.md` | `BookEntityName` |
| `BookIdentity` | `DOC_01_Core.md` | `BookIdentity` |
| `BookTemplateId` | `DOC_01_Core.md` | `BookTemplateId` |
| `CanonicalTemporalText` | `DOC_01_Core.md` | `CanonicalTemporalText` |
| `CausationId` | `DOC_01_Core.md` | `CausationId` |
| `CommandId` | `DOC_01_Core.md` | `CommandId` |
| `CommittedProvenance` | `DOC_01_Core.md` | `CommittedProvenance` |
| `ContentSha256` | `DOC_01_Core_EvidenceAndWire.md` | `ContentSha256` |
| `CorrelationId` | `DOC_01_Core.md` | `CorrelationId` |
| `CurrencyBalance` | `DOC_01_Core.md` | `CurrencyBalance` |
| `CurrencyUnit` | `DOC_01_Core.md` | `CurrencyUnit` |
| `EffectiveDateRange` | `DOC_01_Core.md` | `EffectiveDateRange` |
| `EntityProfile` | `DOC_01_Core.md` | `EntityProfile` |
| `EntityForm` | `DOC_01_Core.md` | `EntityForm` |
| `FinancialPositionLineClassification` | `DOC_01_Core.md` | `FinancialPositionLineClassification` |
| `FiscalYearStart` | `DOC_01_Core.md` | `FiscalYearStart` |
| `IdempotencyKey` | `DOC_01_Core.md` | `IdempotencyKey` |
| `JournalEntry` | `DOC_01_Core.md` | `JournalEntry` |
| `JournalEntryValidationException` | `DOC_01_Core.md` | `JournalEntryValidationException` |
| `JournalLine` | `DOC_01_Core.md` | `JournalLine` |
| `JournalLine.EntrySide` | `DOC_01_Core.md` | `JournalLine.EntrySide` |
| `JournalRecipe` | `DOC_01_Core.md` | `JournalRecipe` |
| `JournalRecipeKind` | `DOC_01_Core.md` | `JournalRecipeKind` |
| `Money` | `DOC_01_Core.md` | `Money` |
| `NormalBalance` | `DOC_01_Core.md` | `NormalBalance` |
| `PositiveMoney` | `DOC_01_Core.md` | `PositiveMoney` |
| `PostingCoverage` | `DOC_01_Core.md` | `PostingCoverage` |
| `PostingId` | `DOC_01_Core.md` | `PostingId` |
| `PostingKind` | `DOC_01_Core.md` | `PostingKind` |
| `PostingOriginKind` | `DOC_01_Core.md` | `PostingOriginKind` |
| `ProfitAndLossLineClassification` | `DOC_01_Core.md` | `ProfitAndLossLineClassification` |
| `ReportingPeriod` | `DOC_01_Core.md` | `ReportingPeriod` |
| `RequestProvenance` | `DOC_01_Core.md` | `RequestProvenance` |
| `ReversalReason` | `DOC_01_Core_EvidenceAndWire.md` | `ReversalReason` |
| `ReversalReference` | `DOC_01_Core_EvidenceAndWire.md` | `ReversalReference` |
| `SourceChannel` | `DOC_01_Core_EvidenceAndWire.md` | `SourceChannel` |
| `SourceDocumentId` | `DOC_01_Core_EvidenceAndWire.md` | `SourceDocumentId` |
| `SourceDocumentReference` | `DOC_01_Core_EvidenceAndWire.md` | `SourceDocumentReference` |
| `SourceDocumentType` | `DOC_01_Core_EvidenceAndWire.md` | `SourceDocumentType` |
| `SourceDocumentTypePolicyMode` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `StatementLineKind` | `DOC_01_Core.md` | `StatementLineKind` |
| `StorageLocator` | `DOC_01_Core_EvidenceAndWire.md` | `StorageLocator` |
| `WireValue` | `DOC_01_Core_EvidenceAndWire.md` | `WireValue` |
| `BookkeepingKernelFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `ApplicationIdentity` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ArtifactOutputDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
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
| `CapabilitiesDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `CommandCatalogDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `CommandDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDecision` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `ContractDiscovery` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscoveryDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractErrors` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `ContractErrors.Descriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailure` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailureException` | `DOC_02_ProtocolAndDiscovery.md` | `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException` |
| `DiscoveryFocus` | `DOC_02_ProtocolAndDiscovery.md` | `DiscoveryFocus` |
| `ContractRequestShapes` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.DeclareAccountRequestShapeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.EntryKindSemanticsDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.EvidenceProfileDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.EvidenceRequirementDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.EnumVocabularyDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.JournalRecipeSemanticsDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.LedgerPlanRequestShapeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.PostEntryRequestShapeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.ReachabilityCellDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestFieldDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestInputDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestShapeDescriptorType` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestShapesDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AccountRegistryDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.BookkeepingKernelDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AuditDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.BookModelDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.CommitGuarantee` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.CurrencyDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ErrorDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.FieldDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.InitializationRequirement` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.PlanExecutionDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.PreflightDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.RejectionDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ResponseDescriptorType` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ResponseModelDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ReversalDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.AccountingEvidenceTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.ApprovalTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.DeclareAccountTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.JournalLineTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractPlanTemplates` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractPlanTemplates.LedgerAssertionTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractPlanTemplates.LedgerPlanStepTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractPlanTemplates.LedgerPlanTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractPlanTemplates.EnsureBookTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.OpeningBalanceTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.PostingRequestTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.ProvenanceTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.ReversalTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.SourceDocumentTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `TemplateDescriptorType` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `TemporalScopeArchetype` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `CurrencyFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `DescriptorNamespaceSupport` | `DOC_02_ProtocolAndDiscovery.md` | `DescriptorNamespaceSupport` |
| `DiscoveryDetail` | `DOC_02_ProtocolAndDiscovery.md` | `DiscoveryDetail` |
| `EnvironmentDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentPublicationDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentRuntimeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.FailedRuntime` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.IncompatibleRuntime` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.ReadyRuntime` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.RuntimeState` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor.UnavailableRuntime` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentStorageDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ExecutionMode` | `DOC_02_ProtocolAndDiscovery.md` | `ExecutionMode` |
| `ExitCodeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `HelpDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `MachineContract` | `DOC_02_ProtocolAndDiscovery.md` | `MachineContract` |
| `MonetaryAmount` | `DOC_02_ProtocolAndDiscovery.md` | `MonetaryAmount` |
| `OperationCategory` | `DOC_02_ProtocolAndDiscovery.md` | `OperationCategory` |
| `OperationId` | `DOC_02_ProtocolAndDiscovery.md` | `OperationId` |
| `OutputMode` | `DOC_02_ProtocolAndDiscovery.md` | `OutputMode` |
| `PlanExecutionFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PlanFailurePolicy` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `PlanResultDetail` | `DOC_02_ProtocolAndDiscovery.md` | `PlanResultDetail` |
| `PlanTransactionMode` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `PreflightFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `ProtectedBookFormatContract` | `DOC_02_ProtocolAndDiscovery.md` | `ProtectedBookFormatContract` |
| `ProtocolArtifactOutput` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolArtifactOutput` |
| `ProtocolCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolCatalog` |
| `ProtocolInteractionLimits` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolInteractionLimits` |
| `ProtocolCommandSignature` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolDeclareAccountFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolDiagnosticCode` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload`, `ProtocolEnvelopeStatus`, And `ProtocolDiagnosticCode` |
| `ProtocolExampleStep` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolExampleStep` |
| `ProtocolEnvelopeStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload`, `ProtocolEnvelopeStatus`, And `ProtocolDiagnosticCode` |
| `ProtocolLedgerPlanFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Assertion` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Plan` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Query` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Step` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolMoneyFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolMoneyFields` |
| `ProtocolOpenBookFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolOperation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOperationDocumentation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOperationOutputs` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOptions` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions` |
| `ProtocolPostEntryFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Approval` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Evidence` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.JournalLine` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.OpeningBalance` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Provenance` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Reversal` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.SourceDocument` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.TopLevel` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolBookRequestFieldSets` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, And `ProtocolLedgerPlanRequestFieldSets` |
| `ProtocolPostingRequestFieldSets` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, And `ProtocolLedgerPlanRequestFieldSets` |
| `ProtocolLedgerPlanRequestFieldSets` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, And `ProtocolLedgerPlanRequestFieldSets` |
| `ProtocolEnvelopeCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolDomainCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolRuntimeCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolDistributionCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolManagedSqliteCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `ProtocolSharedRequestFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSharedRequestFields` |
| `ProtocolSuccessPayload` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessPayload`, `ProtocolEnvelopeStatus`, And `ProtocolDiagnosticCode` |
| `PublicCliBundleTarget` | `DOC_02_ProtocolAndDiscovery.md` | `PublicCliBundleTarget` |
| `PublicCliDistribution` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `PublicBundlePublicationStatus` | `DOC_02_ProtocolAndDiscovery.md` | `PublicBundlePublicationStatus` |
| `ReportCapabilityFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `RequestFieldPresence` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `RequestSurfaceFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.CommandTemporalScopeFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.EvidenceProfileFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.EvidenceRequirementFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.JournalRecipeFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.PostEntryKindFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.ReachabilityCellFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.SourceDocumentTypeFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RequestSurfaceFacts.TemporalScopeFacts` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog` |
| `RuntimeDistribution` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `ScaffoldPlaceholders` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `SelectableOutputDefaultsDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `SqliteCompileOptionsVerificationStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `SqliteLibraryMode` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeArtifactEvidence` | `DOC_02_ProtocolAndDiscovery.md` | `SqliteRuntimeArtifactEvidence` |
| `SqliteRuntimeProvenance` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeStateValidator` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeStatus` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `SqliteRuntimeTrustBasis` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `StorageDriver` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `StorageEngine` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator` |
| `StorageSurfaceDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `VersionDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `WorkflowDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowStepDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowStepKind` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `WorkflowSurface` | `DOC_02_ProtocolAndDiscovery.md` | `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor` |
| `AccountBalanceQuery` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceQuery` |
| `AccountBalanceResult` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceResult` |
| `AccountBalanceSnapshot` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceSnapshot` |
| `AccountLedgerEntry` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerQuery` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerReport` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerResult` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountPage` | `DOC_02_AdministrationAndReports.md` | `AccountPage` |
| `AccountPageCursor` | `DOC_02_AdministrationAndReports.md` | `AccountPageCursor` |
| `BackupBookResult` | `DOC_02_AdministrationAndReports.md` | `BackupBookResult` |
| `BookAdministrationRejection` | `DOC_02_AdministrationAndReports.md` | `BookAdministrationRejection` |
| `BookAdministrationService` | `DOC_02_AdministrationAndReports.md` | `BookAdministrationService` |
| `BookInspection` | `DOC_02_AdministrationAndReports.md` | `BookInspection` |
| `BookTemplateAccounts` | `DOC_02_AdministrationAndReports.md` | `BookTemplateAccounts` |
| `BookMaintenanceArtifactRole` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint` |
| `BookMaintenancePathFailure` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint` |
| `BookMaintenanceRejection` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint` |
| `BookMaintenanceVerificationFailure` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint` |
| `BookMigrationPolicy` | `DOC_02_AdministrationAndReports.md` | `BookMigrationPolicy` |
| `BookMigrationPolicyMode` | `DOC_02_AdministrationAndReports.md` | `BookMigrationPolicy` |
| `BookQueryRejection` | `DOC_02_AdministrationAndReports.md` | `BookQueryRejection` |
| `BookReadService` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
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
| `PeriodResultTransferCommand` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferCommand`, `PeriodResultTransferResult`, And `TransferredPeriodResult` |
| `PeriodResultTransferResult` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferCommand`, `PeriodResultTransferResult`, And `TransferredPeriodResult` |
| `TransferredPeriodResult` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferCommand`, `PeriodResultTransferResult`, And `TransferredPeriodResult` |
| `DeclareAccountCommand` | `DOC_02_AdministrationAndReports.md` | `DeclareAccountCommand` |
| `DeclareAccountResult` | `DOC_02_AdministrationAndReports.md` | `DeclareAccountResult` |
| `DeclaredAccount` | `DOC_02_AdministrationAndReports.md` | `DeclaredAccount` |
| `FinancialPositionCriteria` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionQuery` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionReport` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionResult` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionRow` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionRowView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionSection` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult` |
| `FinancialPositionSectionView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `FinancialPositionView` | `DOC_02_AdministrationAndReports.md` | `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView` |
| `GetPostingResult` | `DOC_02_AdministrationAndReports.md` | `GetPostingResult` |
| `IncomeStatementCriteria` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementQuery` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementReport` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementResult` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementRow` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementRowView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementSection` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult` |
| `IncomeStatementSectionView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `IncomeStatementView` | `DOC_02_AdministrationAndReports.md` | `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView` |
| `ListAccountsQuery` | `DOC_02_AdministrationAndReports.md` | `ListAccountsQuery` |
| `ListAccountsResult` | `DOC_02_AdministrationAndReports.md` | `ListAccountsResult` |
| `ListPostingsQuery` | `DOC_02_AdministrationAndReports.md` | `ListPostingsQuery` |
| `ListPostingsResult` | `DOC_02_AdministrationAndReports.md` | `ListPostingsResult` |
| `OpenBookCommand` | `DOC_02_AdministrationAndReports.md` | `OpenBookCommand` |
| `OpenBookResult` | `DOC_02_AdministrationAndReports.md` | `OpenBookResult` |
| `PeriodAccountActivityRow` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodResultTransferDraft` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService` |
| `PeriodResultTransferOutcome` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService` |
| `ResultHoldingSelection` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService` |
| `AcceptedResultHoldingSelection` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService` |
| `RejectedResultHoldingSelection` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService` |
| `PeriodResultTransferPlan` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService` |
| `PeriodResultTransferPlanner` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService` |
| `PeriodResultTransferService` | `DOC_02_AdministrationAndReports.md` | `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService` |
| `PeriodCurrencySummary` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryQuery` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryReport` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryResult` | `DOC_02_AdministrationAndReports.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PostingPage` | `DOC_02_AdministrationAndReports.md` | `PostingPage` |
| `PostingPageCursor` | `DOC_02_AdministrationAndReports.md` | `PostingPageCursor` |
| `PublicPathHint` | `DOC_02_AdministrationAndReports.md` | `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint` |
| `RejectionNarrative` | `DOC_02_AdministrationAndReports.md` | `RejectionNarrative` |
| `RekeyBookResult` | `DOC_02_AdministrationAndReports.md` | `RekeyBookResult` |
| `RekeyRollbackResult` | `DOC_02_AdministrationAndReports.md` | `RekeyRollbackResult` |
| `RestoreBookResult` | `DOC_02_AdministrationAndReports.md` | `RestoreBookResult` |
| `TrialBalanceQuery` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceReport` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceResult` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceRow` | `DOC_02_AdministrationAndReports.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `BookkeepingAdministrationRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingEntry` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingEntry` And `BookkeepingEntryKind` |
| `BookkeepingEntryKind` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingEntry` And `BookkeepingEntryKind` |
| `BookkeepingEntry.OpeningAccountBalance` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingEntry` And `BookkeepingEntryKind` |
| `BookkeepingPostingRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingPublishedLanguageTranslator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator` |
| `CommitEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `LedgerAssertion` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerAssertion` |
| `LedgerAssertionKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerBoundaryPhase` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerExecutionJournal` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerFact` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerFact` |
| `LedgerFactKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerFactKind` |
| `LedgerJournalEntry` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerJournalKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerJournalStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerPlan` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlan` |
| `LedgerPlanId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerPlanResult` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerPlanService` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanService` |
| `LedgerPlanStatus` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStep` |
| `LedgerStepFailure` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerStepId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerStepKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerStepStatus` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `PostEntryCommand` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryCommand` |
| `PostEntryCommandTranslator` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryCommandTranslator` |
| `PostEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PostingAcceptancePolicy` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator` |
| `PostingApplicationService` | `DOC_02_PostingAndLedgerPlans.md` | `PostingApplicationService` |
| `PostingCommand` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PostingDraft` | `DOC_02_PostingAndLedgerPlans.md` | `PostingDraft` |
| `PostingIdGenerator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingIdGenerator` |
| `PostingLineage` | `DOC_02_PostingAndLedgerPlans.md` | `PostingLineage` |
| `PostingLineageModel` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PostingRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection` And `PostingRejectionSemantics` |
| `PostingRejectionSemantics` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection` And `PostingRejectionSemantics` |
| `PostingRequestModel` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PreflightEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `UuidV7PostingIdGenerator` | `DOC_02_PostingAndLedgerPlans.md` | `UuidV7PostingIdGenerator` |
| `AccountBalanceCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountBalanceView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountCatalogStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `AccountCurrencyTotals` | `DOC_03_BookSessionsAndAdapters.md` | `AccountCurrencyTotals` |
| `AccountDeclaration` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `AccountDeclarationOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `AccountLedgerCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountLedgerEntryView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountLedgerView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountLookupStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `AccountRegistryCursor` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountRegistryPage` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `AccountRegistryQuery` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `BookAccess` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` And `BookAccess.PassphraseSource` |
| `BookAccess.PassphraseSource` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` And `BookAccess.PassphraseSource` |
| `BookAdministrationStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `BookAuditEvent` | `DOC_03_BookSessionsAndAdapters.md` | `BookAuditEvent` And `BookAuditEventKind` |
| `BookAuditEventKind` | `DOC_03_BookSessionsAndAdapters.md` | `BookAuditEvent` And `BookAuditEventKind` |
| `BookInspectionPublishedLanguageTranslator` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleInspection` And `BookInspectionPublishedLanguageTranslator` |
| `BookLifecycleInspection` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleInspection` And `BookInspectionPublishedLanguageTranslator` |
| `BookLifecycleReader` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `BookOpeningOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `BookkeepingQueryRejection` | `DOC_03_BookSessionsAndAdapters.md` | `BookkeepingQueryRejection` |
| `BookkeepingReadStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `BookkeepingReportStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `ChartOfAccounts` | `DOC_03_BookSessionsAndAdapters.md` | `ChartOfAccounts` |
| `CommittedPosting` | `DOC_03_BookSessionsAndAdapters.md` | `CommittedPosting` |
| `LedgerPlanTransaction` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `MaintenanceCompletion` | `DOC_03_BookSessionsAndAdapters.md` | `MaintenanceDecision`, `MaintenanceCompletion`, And `MaintenanceFailure` |
| `MaintenanceDecision` | `DOC_03_BookSessionsAndAdapters.md` | `MaintenanceDecision`, `MaintenanceCompletion`, And `MaintenanceFailure` |
| `MaintenanceFailure` | `DOC_03_BookSessionsAndAdapters.md` | `MaintenanceDecision`, `MaintenanceCompletion`, And `MaintenanceFailure` |
| `ManagedSqliteRuntimeUnavailableException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, And `SqliteStorageFailureException` |
| `PeriodAccountActivityView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PeriodResultTransferStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `PeriodCurrencySummaryView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PeriodSummaryCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PeriodSummaryView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PostingCommitResult` | `DOC_03_BookSessionsAndAdapters.md` | `PostingDraft`, `PostingCommitResult`, And `PostingIdGenerator` |
| `PostingCommitStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `PostingFact` | `DOC_03_BookSessionsAndAdapters.md` | `PostingFact` |
| `PostingHistoryCursor` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PostingHistoryPage` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PostingHistoryQuery` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `PostingHistoryStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `PostingLookupStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `PostingRangeStore` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction` |
| `PostingValidationStore` | `DOC_03_BookSessionsAndAdapters.md` | `PostingValidationStore` |
| `ProtectedBookAccess` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookAccess` |
| `ProtectedBookBackupOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookBackupOutcome`, `ProtectedBookRestoreOutcome`, And `ProtectedBookRecoveryOutcome` |
| `ProtectedBookMaintenanceArtifactRole` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenancePathFailure`, `ProtectedBookMaintenanceRejection`, `ProtectedBookMaintenanceRejectionException`, And `ProtectedBookMaintenanceWorkflow` |
| `ProtectedBookMaintenancePathFailure` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenancePathFailure`, `ProtectedBookMaintenanceRejection`, `ProtectedBookMaintenanceRejectionException`, And `ProtectedBookMaintenanceWorkflow` |
| `ProtectedBookMaintenanceAuditCompensationKind` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceAuditCompensationKind` |
| `ProtectedBookMaintenanceAuditKind` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceAuditKind` |
| `ProtectedBookMaintenancePublishedLanguageTranslator` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenancePublishedLanguageTranslator` And `ProtectedBookVerificationFailure` |
| `ProtectedBookMaintenanceRejection` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenancePathFailure`, `ProtectedBookMaintenanceRejection`, `ProtectedBookMaintenanceRejectionException`, And `ProtectedBookMaintenanceWorkflow` |
| `ProtectedBookMaintenanceRejectionException` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenancePathFailure`, `ProtectedBookMaintenanceRejection`, `ProtectedBookMaintenanceRejectionException`, And `ProtectedBookMaintenanceWorkflow` |
| `ProtectedBookMaintenanceService` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, `StagedBackupPair`, `StagedBookReplacement`, `StagedRollbackArtifactDeletion`, And `SqliteProtectedBookMaintenanceStore` |
| `ProtectedBookMaintenanceStore` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, `StagedBackupPair`, `StagedBookReplacement`, `StagedRollbackArtifactDeletion`, And `SqliteProtectedBookMaintenanceStore` |
| `ProtectedBookMaintenanceWorkflow` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenancePathFailure`, `ProtectedBookMaintenanceRejection`, `ProtectedBookMaintenanceRejectionException`, And `ProtectedBookMaintenanceWorkflow` |
| `ProtectedBookPassphraseSource` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookAccess` And `ProtectedBookPassphraseSource` |
| `ProtectedBookRecoveryOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookBackupOutcome`, `ProtectedBookRestoreOutcome`, And `ProtectedBookRecoveryOutcome` |
| `ProtectedBookRestoreOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookBackupOutcome`, `ProtectedBookRestoreOutcome`, And `ProtectedBookRecoveryOutcome` |
| `ProtectedBookVerificationFailure` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenancePublishedLanguageTranslator` And `ProtectedBookVerificationFailure` |
| `RegisteredAccount` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `SqliteAdministrationSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteAdministrationSessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `GeneratedBookKeyFile` | `DOC_03_BookSessionsAndAdapters.md` | `GeneratedBookKeyFile`, `SqliteBookKeyFile`, And `SqliteBookKeyFileGenerator` |
| `SqliteBookKeyFile` | `DOC_03_BookSessionsAndAdapters.md` | `GeneratedBookKeyFile`, `SqliteBookKeyFile`, And `SqliteBookKeyFileGenerator` |
| `SqliteBookKeyFileGenerator` | `DOC_03_BookSessionsAndAdapters.md` | `GeneratedBookKeyFile`, `SqliteBookKeyFile`, And `SqliteBookKeyFileGenerator` |
| `SqliteBookPassphrase` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookPassphrase` |
| `SqliteBookPassphraseSourceBytes` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookPassphraseSourceBytes`, And `SqliteBookPassphraseSourceBytes.OversizedBookPassphraseSourceException` |
| `SqliteBookPassphraseSourceBytes.OversizedBookPassphraseSourceException` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookPassphraseSourceBytes`, And `SqliteBookPassphraseSourceBytes.OversizedBookPassphraseSourceException` |
| `SqliteBookSessionMode` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteBookSessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteFailureClassifier` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteFailureClassifier` And `SqliteFailureClassifier.Category` |
| `SqliteFailureClassifier.Category` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteFailureClassifier` And `SqliteFailureClassifier.Category` |
| `SqlitePassphraseIntent` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePassphraseResolver` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePeriodResultTransferSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePeriodResultTransferSessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePlanExecutionSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePlanExecutionSessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePersistenceInvariantException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, And `SqliteStorageFailureException` |
| `SqlitePostingSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePostingSessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteProtectedBookMaintenanceStore` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, `StagedBackupPair`, `StagedBookReplacement`, `StagedRollbackArtifactDeletion`, And `SqliteProtectedBookMaintenanceStore` |
| `StagedBackupPair` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, `StagedBackupPair`, `StagedBookReplacement`, `StagedRollbackArtifactDeletion`, And `SqliteProtectedBookMaintenanceStore` |
| `StagedBookReplacement` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, `StagedBackupPair`, `StagedBookReplacement`, `StagedRollbackArtifactDeletion`, And `SqliteProtectedBookMaintenanceStore` |
| `StagedRollbackArtifactDeletion` | `DOC_03_BookSessionsAndAdapters.md` | `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, `StagedBackupPair`, `StagedBookReplacement`, `StagedRollbackArtifactDeletion`, And `SqliteProtectedBookMaintenanceStore` |
| `SqliteReadSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteReadSessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteRekeySession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteRekeySessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteRuntime` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteRuntime.Probe` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteRuntime.Status` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteStorageFailureException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, And `SqliteStorageFailureException` |
| `TrialBalanceCriteria` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `TrialBalanceRowView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `TrialBalanceView` | `DOC_03_BookSessionsAndAdapters.md` | `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView` |
| `UnsupportedManagedSqliteLibraryIdentityException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, And `SqliteStorageFailureException` |
| `UnsupportedSqliteCompileOptionsException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, And `SqliteStorageFailureException` |
| `App` | `DOC_04_CliAndPdfAdapters.md` | `App` |
| `PdfReportService` | `DOC_04_CliAndPdfAdapters.md` | `PdfReportService` |
