---
afad: "4.0"
version: "0.30.0"
domain: INDEX
updated: "2026-05-05"
route:
  keywords: [fingrind, index, routing, api, symbols, core, contract, executor, sqlite, cli, report-pdf, machine-contract, book-session]
  questions: ["where is the fingrind api documented", "which doc file covers BookReadSession", "which doc file covers RequestProvenance", "which doc file covers ProtocolCatalog", "which doc file covers PdfReportService"]
---

# API Index

**Purpose**: Route readers to the current public FinGrind API reference atoms.

## File Index

| File | Scope |
|:-----|:------|
| `DOC_01_Core.md` | exported accounting vocabulary and invariants from the `core` module |
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
| `AccountName` | `DOC_01_Core.md` | `AccountName` |
| `ActorId` | `DOC_01_Core.md` | `ActorId` |
| `ActorType` | `DOC_01_Core.md` | `ActorType` |
| `BalanceSide` | `DOC_01_Core.md` | `BalanceSide` |
| `CausationId` | `DOC_01_Core.md` | `CausationId` |
| `CommandId` | `DOC_01_Core.md` | `CommandId` |
| `CommittedProvenance` | `DOC_01_Core.md` | `CommittedProvenance` |
| `CorrelationId` | `DOC_01_Core.md` | `CorrelationId` |
| `CurrencyCode` | `DOC_01_Core.md` | `CurrencyCode` |
| `IdempotencyKey` | `DOC_01_Core.md` | `IdempotencyKey` |
| `JournalEntry` | `DOC_01_Core.md` | `JournalEntry` |
| `JournalEntryValidationException` | `DOC_01_Core.md` | `JournalEntryValidationException` |
| `JournalLine` | `DOC_01_Core.md` | `JournalLine` |
| `JournalLine.EntrySide` | `DOC_01_Core.md` | `JournalLine.EntrySide` |
| `Money` | `DOC_01_Core.md` | `Money` |
| `PositiveMoney` | `DOC_01_Core.md` | `PositiveMoney` |
| `NormalBalance` | `DOC_01_Core.md` | `NormalBalance` |
| `PostingId` | `DOC_01_Core.md` | `PostingId` |
| `RequestProvenance` | `DOC_01_Core.md` | `RequestProvenance` |
| `ReversalReason` | `DOC_01_Core.md` | `ReversalReason` |
| `ReversalReference` | `DOC_01_Core.md` | `ReversalReference` |
| `SourceChannel` | `DOC_01_Core.md` | `SourceChannel` |
| `WireValue` | `DOC_01_Core.md` | `WireValue` |
| `ProtocolCatalog` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolCatalog` |
| `ProtocolOperation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolCommandSignature` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOperationOutputs` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `ProtocolOperationDocumentation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOperation` |
| `OperationId` | `DOC_02_ProtocolAndDiscovery.md` | `OperationId` |
| `OperationCategory` | `DOC_02_ProtocolAndDiscovery.md` | `OperationCategory` |
| `ExecutionMode` | `DOC_02_ProtocolAndDiscovery.md` | `ExecutionMode` |
| `OutputMode` | `DOC_02_ProtocolAndDiscovery.md` | `OutputMode` |
| `ProtocolSuccessStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, And `ProtocolFailureStatus` |
| `ProtocolRejectionStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, And `ProtocolFailureStatus` |
| `ProtocolFailureStatus` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, And `ProtocolFailureStatus` |
| `ProtocolLimits` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolLimits` |
| `ProtocolOptions` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions` |
| `ProtocolArtifactOutput` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolArtifactOutput` |
| `PublicDistributionContract` | `DOC_02_ProtocolAndDiscovery.md` | `PublicDistributionContract` |
| `PublicCliBundleTarget` | `DOC_02_ProtocolAndDiscovery.md` | `PublicCliBundleTarget` |
| `PlanTransactionMode` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `PlanFailurePolicy` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `RuntimeDistribution` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `PublicCliDistribution` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `StorageDriver` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `StorageEngine` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `BookProtectionMode` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `BookCipher` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `SqliteLibraryMode` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `SqliteRuntimeProvenance` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `SqliteRuntimeStatus` | `DOC_02_ProtocolAndDiscovery.md` | `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, And `SqliteRuntimeStatus` |
| `BookModelFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookBoundaryFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookEntityScopeFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookFilesystemFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookCredentialFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookInitializationFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookAccountRegistryFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `BookCurrencyScopeFact` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `CurrencyFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PreflightFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PlanExecutionFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `ProtocolSharedRequestFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolSharedRequestFields` |
| `ProtocolDeclareAccountFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.TopLevel` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.JournalLine` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Provenance` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Reversal` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Plan` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Step` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Query` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Assertion` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
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
| `ExitCodeDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentDistributionDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentStorageDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `EnvironmentSqliteDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
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
| `ContractTemplates` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.TemplateDescriptorType` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.PostingRequestTemplateDescriptor` | `DOC_02_ProtocolAndDiscovery.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
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
| `BookFormatContract` | `DOC_02_ProtocolAndDiscovery.md` | `BookFormatContract` |
| `ProtectedBookFormatContract` | `DOC_02_ProtocolAndDiscovery.md` | `ProtectedBookFormatContract` |
| `BookAdministrationService` | `DOC_02_AdministrationAndReports.md` | `BookAdministrationService` |
| `BookReadService` | `DOC_02_AdministrationAndReports.md` | `BookReadService` |
| `DeclareAccountCommand` | `DOC_02_AdministrationAndReports.md` | `DeclareAccountCommand` |
| `DeclaredAccount` | `DOC_02_AdministrationAndReports.md` | `DeclaredAccount` |
| `OpenBookResult` | `DOC_02_AdministrationAndReports.md` | `OpenBookResult` |
| `DeclareAccountResult` | `DOC_02_AdministrationAndReports.md` | `DeclareAccountResult` |
| `RekeyBookResult` | `DOC_02_AdministrationAndReports.md` | `RekeyBookResult` |
| `BookInspection` | `DOC_02_AdministrationAndReports.md` | `BookInspection` |
| `ListAccountsQuery` | `DOC_02_AdministrationAndReports.md` | `ListAccountsQuery` |
| `AccountPageCursor` | `DOC_02_AdministrationAndReports.md` | `AccountPageCursor` |
| `AccountPage` | `DOC_02_AdministrationAndReports.md` | `AccountPage` |
| `ListAccountsResult` | `DOC_02_AdministrationAndReports.md` | `ListAccountsResult` |
| `GetPostingResult` | `DOC_02_AdministrationAndReports.md` | `GetPostingResult` |
| `EffectiveDateRange` | `DOC_02_AdministrationAndReports.md` | `EffectiveDateRange` |
| `PostingPageCursor` | `DOC_02_AdministrationAndReports.md` | `PostingPageCursor` |
| `ListPostingsQuery` | `DOC_02_AdministrationAndReports.md` | `ListPostingsQuery` |
| `PostingPage` | `DOC_02_AdministrationAndReports.md` | `PostingPage` |
| `ListPostingsResult` | `DOC_02_AdministrationAndReports.md` | `ListPostingsResult` |
| `AccountBalanceQuery` | `DOC_02_AdministrationAndReports.md` | `AccountBalanceQuery` |
| `CurrencyBalance` | `DOC_02_AdministrationAndReports.md` | `CurrencyBalance` |
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
| `BookAdministrationRejection` | `DOC_02_AdministrationAndReports.md` | `BookAdministrationRejection` |
| `BookQueryRejection` | `DOC_02_AdministrationAndReports.md` | `BookQueryRejection` |
| `RejectionNarrative` | `DOC_02_AdministrationAndReports.md` | `RejectionNarrative` |
| `PostingLineage` | `DOC_02_PostingAndLedgerPlans.md` | `PostingLineage` |
| `PostEntryCommand` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryCommand` |
| `PostEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PreflightEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `CommitEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PostingRequest` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRequest` |
| `PostingDraft` | `DOC_02_PostingAndLedgerPlans.md` | `PostingDraft` |
| `PostingCommand` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PostingLineageModel` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PostingRequestModel` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel` |
| `PostingAcceptancePolicy` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy` And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingPublishedLanguageTranslator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy` And `BookkeepingPublishedLanguageTranslator` |
| `LedgerPlanId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerStepId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerPlan` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlan` |
| `LedgerStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStep` |
| `LedgerAssertion` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerAssertion` |
| `BookWorkflowPlan` | `DOC_02_PostingAndLedgerPlans.md` | `BookWorkflowPlan`, `BookWorkflowStep`, And `BookWorkflowAssertion` |
| `BookWorkflowStep` | `DOC_02_PostingAndLedgerPlans.md` | `BookWorkflowPlan`, `BookWorkflowStep`, And `BookWorkflowAssertion` |
| `BookWorkflowAssertion` | `DOC_02_PostingAndLedgerPlans.md` | `BookWorkflowPlan`, `BookWorkflowStep`, And `BookWorkflowAssertion` |
| `BookWorkflowPublishedLanguageTranslator` | `DOC_02_PostingAndLedgerPlans.md` | `BookWorkflowPublishedLanguageTranslator` |
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
| `PostingIdGenerator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingIdGenerator` |
| `UuidV7PostingIdGenerator` | `DOC_02_PostingAndLedgerPlans.md` | `UuidV7PostingIdGenerator` |
| `PostingRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection` |
| `BookAccess` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` And `BookAccess.PassphraseSource` |
| `BookAccess.PassphraseSource` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` And `BookAccess.PassphraseSource` |
| `PostingFact` | `DOC_03_BookSessionsAndAdapters.md` | `PostingFact` |
| `CommittedPosting` | `DOC_03_BookSessionsAndAdapters.md` | `CommittedPosting` |
| `AccountDeclaration` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `AccountDeclarationOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `BookOpeningOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `RegisteredAccount` | `DOC_03_BookSessionsAndAdapters.md` | `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount` |
| `BookAdministrationSession` | `DOC_03_BookSessionsAndAdapters.md` | `BookAdministrationSession` |
| `PostingValidationBook` | `DOC_03_BookSessionsAndAdapters.md` | `PostingValidationBook` |
| `PostingBookSession` | `DOC_03_BookSessionsAndAdapters.md` | `PostingBookSession` |
| `BookReadSession` | `DOC_03_BookSessionsAndAdapters.md` | `BookReadSession` |
| `LedgerPlanSession` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanSession` |
| `PostingCommitResult` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult` |
| `SqliteBookPassphrase` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookPassphrase` |
| `SqliteBookKeyFile` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookKeyFile`, `SqliteBookKeyFileGenerator`, And `SqliteBookKeyFileGenerator.GeneratedKeyFile` |
| `SqliteBookKeyFileGenerator` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookKeyFile`, `SqliteBookKeyFileGenerator`, And `SqliteBookKeyFileGenerator.GeneratedKeyFile` |
| `SqliteBookKeyFileGenerator.GeneratedKeyFile` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookKeyFile`, `SqliteBookKeyFileGenerator`, And `SqliteBookKeyFileGenerator.GeneratedKeyFile` |
| `SqliteRuntime` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteRuntime.Probe` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteRuntime.Status` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status` |
| `SqliteFailureClassifier` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteFailureClassifier` And `SqliteFailureClassifier.Category` |
| `SqliteFailureClassifier.Category` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteFailureClassifier` And `SqliteFailureClassifier.Category` |
| `ManagedSqliteRuntimeUnavailableException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException` |
| `UnsupportedSqliteCompileOptionsException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException` |
| `SqliteStorageFailureException` | `DOC_03_BookSessionsAndAdapters.md` | `ManagedSqliteRuntimeUnavailableException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException` |
| `SqliteBookSession` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookSession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteBookSessionMode` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookSession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePassphraseIntent` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookSession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqlitePassphraseResolver` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookSession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `SqliteBookSessions` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookSession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions` |
| `App` | `DOC_04_CliAndPdfAdapters.md` | `App` |
| `PdfReportService` | `DOC_04_CliAndPdfAdapters.md` | `PdfReportService` |
