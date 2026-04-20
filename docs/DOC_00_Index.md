---
afad: "3.5"
version: "0.19.0"
domain: INDEX
updated: "2026-04-19"
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
| `DOC_02_Application.md` | exported `contract` models, protocol metadata, discovery namespaces, reports, ledger plans, rejections, and exported `executor` services |
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
| `ProtocolCatalog` | `DOC_02_Application.md` | `ProtocolCatalog` |
| `ProtocolOperation` | `DOC_02_Application.md` | `ProtocolOperation` |
| `OperationId` | `DOC_02_Application.md` | `OperationId` |
| `OperationCategory` | `DOC_02_Application.md` | `OperationCategory` |
| `ExecutionMode` | `DOC_02_Application.md` | `ExecutionMode` |
| `OutputMode` | `DOC_02_Application.md` | `OutputMode` |
| `ProtocolStatuses` | `DOC_02_Application.md` | `ProtocolStatuses` |
| `ProtocolLimits` | `DOC_02_Application.md` | `ProtocolLimits` |
| `ProtocolOptions` | `DOC_02_Application.md` | `ProtocolOptions` |
| `ProtocolArtifactOutput` | `DOC_02_Application.md` | `ProtocolArtifactOutput` |
| `PublicDistributionContract` | `DOC_02_Application.md` | `PublicDistributionContract` |
| `BookModelFacts` | `DOC_02_Application.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `CurrencyFacts` | `DOC_02_Application.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PreflightFacts` | `DOC_02_Application.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PlanExecutionFacts` | `DOC_02_Application.md` | `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `ProtocolDeclareAccountFields` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.TopLevel` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.JournalLine` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Provenance` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolPostEntryFields.Reversal` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Plan` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Step` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Query` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `ProtocolLedgerPlanFields.Assertion` | `DOC_02_Application.md` | `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields` |
| `MachineContract` | `DOC_02_Application.md` | `MachineContract` |
| `ContractDiscovery` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscovery.ApplicationIdentity` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscovery.HelpDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscovery.CapabilitiesDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscovery.VersionDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscovery.ArtifactOutputDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscovery.CommandDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscovery.ExitCodeDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractDiscovery.EnvironmentDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestInputDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestShapesDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.PostEntryRequestShapeDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.DeclareAccountRequestShapeDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.LedgerPlanRequestShapeDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.RequestFieldDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractRequestShapes.EnumVocabularyDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.BookModelDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.FieldDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ErrorDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ResponseModelDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.PlanExecutionDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.RejectionDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AuditDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AccountRegistryDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ReversalDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.PreflightDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.CurrencyDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.PostingRequestTemplateDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.JournalLineTemplateDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.ProvenanceTemplateDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.ReversalTemplateDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.LedgerPlanTemplateDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.LedgerPlanStepTemplateDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.DeclareAccountTemplateDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractTemplates.LedgerAssertionTemplateDescriptor` | `DOC_02_Application.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractErrors` | `DOC_02_Application.md` | `ContractErrors` And `ContractErrorException` |
| `ContractErrors.Descriptor` | `DOC_02_Application.md` | `ContractErrors` And `ContractErrorException` |
| `ContractErrorException` | `DOC_02_Application.md` | `ContractErrors` And `ContractErrorException` |
| `BookMigrationPolicy` | `DOC_02_Application.md` | `BookMigrationPolicy` |
| `BookAdministrationService` | `DOC_02_Application.md` | `BookAdministrationService` |
| `BookReadService` | `DOC_02_Application.md` | `BookReadService` |
| `DeclareAccountCommand` | `DOC_02_Application.md` | `DeclareAccountCommand` |
| `DeclaredAccount` | `DOC_02_Application.md` | `DeclaredAccount` |
| `OpenBookResult` | `DOC_02_Application.md` | `OpenBookResult` |
| `DeclareAccountResult` | `DOC_02_Application.md` | `DeclareAccountResult` |
| `RekeyBookResult` | `DOC_02_Application.md` | `RekeyBookResult` |
| `BookInspection` | `DOC_02_Application.md` | `BookInspection` |
| `ListAccountsQuery` | `DOC_02_Application.md` | `ListAccountsQuery` |
| `AccountPage` | `DOC_02_Application.md` | `AccountPage` |
| `ListAccountsResult` | `DOC_02_Application.md` | `ListAccountsResult` |
| `GetPostingResult` | `DOC_02_Application.md` | `GetPostingResult` |
| `EffectiveDateRange` | `DOC_02_Application.md` | `EffectiveDateRange` |
| `PostingPageCursor` | `DOC_02_Application.md` | `PostingPageCursor` |
| `ListPostingsQuery` | `DOC_02_Application.md` | `ListPostingsQuery` |
| `PostingPage` | `DOC_02_Application.md` | `PostingPage` |
| `ListPostingsResult` | `DOC_02_Application.md` | `ListPostingsResult` |
| `AccountBalanceQuery` | `DOC_02_Application.md` | `AccountBalanceQuery` |
| `CurrencyBalance` | `DOC_02_Application.md` | `CurrencyBalance` |
| `AccountBalanceSnapshot` | `DOC_02_Application.md` | `AccountBalanceSnapshot` |
| `AccountBalanceResult` | `DOC_02_Application.md` | `AccountBalanceResult` |
| `TrialBalanceQuery` | `DOC_02_Application.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceRow` | `DOC_02_Application.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceReport` | `DOC_02_Application.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `TrialBalanceResult` | `DOC_02_Application.md` | `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult` |
| `AccountLedgerQuery` | `DOC_02_Application.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerEntry` | `DOC_02_Application.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerReport` | `DOC_02_Application.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerResult` | `DOC_02_Application.md` | `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `PeriodSummaryQuery` | `DOC_02_Application.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodCurrencySummary` | `DOC_02_Application.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodAccountActivityRow` | `DOC_02_Application.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryReport` | `DOC_02_Application.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `PeriodSummaryResult` | `DOC_02_Application.md` | `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult` |
| `BookAdministrationRejection` | `DOC_02_Application.md` | `BookAdministrationRejection` |
| `BookQueryRejection` | `DOC_02_Application.md` | `BookQueryRejection` |
| `RejectionNarrative` | `DOC_02_Application.md` | `RejectionNarrative` |
| `PostingLineage` | `DOC_02_Application.md` | `PostingLineage` |
| `PostEntryCommand` | `DOC_02_Application.md` | `PostEntryCommand` |
| `PostEntryResult` | `DOC_02_Application.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PreflightEntryResult` | `DOC_02_Application.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `CommitEntryResult` | `DOC_02_Application.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PostingRequest` | `DOC_02_Application.md` | `PostingRequest` |
| `PostingDraft` | `DOC_02_Application.md` | `PostingDraft` |
| `LedgerPlanId` | `DOC_02_Application.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerStepId` | `DOC_02_Application.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerPlan` | `DOC_02_Application.md` | `LedgerPlan` |
| `LedgerStep` | `DOC_02_Application.md` | `LedgerStep` |
| `LedgerAssertion` | `DOC_02_Application.md` | `LedgerAssertion` |
| `LedgerFact` | `DOC_02_Application.md` | `LedgerFact` |
| `LedgerStepKind` | `DOC_02_Application.md` | `LedgerStepKind`, `LedgerAssertionKind`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerAssertionKind` | `DOC_02_Application.md` | `LedgerStepKind`, `LedgerAssertionKind`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerStepStatus` | `DOC_02_Application.md` | `LedgerStepKind`, `LedgerAssertionKind`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerPlanStatus` | `DOC_02_Application.md` | `LedgerStepKind`, `LedgerAssertionKind`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerJournalEntry` | `DOC_02_Application.md` | `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerExecutionJournal` | `DOC_02_Application.md` | `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerStepFailure` | `DOC_02_Application.md` | `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `LedgerPlanResult` | `DOC_02_Application.md` | `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult` |
| `PostingApplicationService` | `DOC_02_Application.md` | `PostingApplicationService` |
| `LedgerPlanService` | `DOC_02_Application.md` | `LedgerPlanService` |
| `PostingIdGenerator` | `DOC_02_Application.md` | `PostingIdGenerator` |
| `UuidV7PostingIdGenerator` | `DOC_02_Application.md` | `UuidV7PostingIdGenerator` |
| `PostingRejection` | `DOC_02_Application.md` | `PostingRejection` |
| `BookAccess` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` And `BookAccess.PassphraseSource` |
| `BookAccess.PassphraseSource` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` And `BookAccess.PassphraseSource` |
| `PostingFact` | `DOC_03_BookSessionsAndAdapters.md` | `PostingFact` |
| `BookAdministrationSession` | `DOC_03_BookSessionsAndAdapters.md` | `BookAdministrationSession` |
| `PostingValidationBook` | `DOC_03_BookSessionsAndAdapters.md` | `PostingValidationBook` |
| `PostingBookSession` | `DOC_03_BookSessionsAndAdapters.md` | `PostingBookSession` |
| `BookReadSession` | `DOC_03_BookSessionsAndAdapters.md` | `BookReadSession` |
| `LedgerPlanSession` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanSession` |
| `PostingCommitResult` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult` |
| `PostingValidation` | `DOC_03_BookSessionsAndAdapters.md` | `PostingValidation` |
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
| `SqlitePostingFactStore` | `DOC_03_BookSessionsAndAdapters.md` | `SqlitePostingFactStore` And `SqliteStoreAccessMode` |
| `SqliteStoreAccessMode` | `DOC_03_BookSessionsAndAdapters.md` | `SqlitePostingFactStore` And `SqliteStoreAccessMode` |
| `App` | `DOC_04_CliAndPdfAdapters.md` | `App` |
| `PdfReportService` | `DOC_04_CliAndPdfAdapters.md` | `PdfReportService` |
