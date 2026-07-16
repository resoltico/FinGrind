---
afad: "4.0"
version: "0.60.0"
domain: INDEX_RESPONSE_AND_WORKFLOW
updated: "2026-07-14"
route:
  keywords: [fingrind, response, envelope, failure category, ledger plan, workflow, execution journal]
  questions: ["where is the fingrind response contract documented", "which doc covers LedgerPlanFailure", "which doc covers ContractResponseCatalog"]
---

# Response And Workflow Index

**Purpose**: Route readers to public response-envelope and ledger-workflow contract symbols.
**Prerequisites**: Use [DOC_00_Index.md](./DOC_00_Index.md) for the complete bounded-context map.

| Symbol | File | Section |
|:-------|:-----|:--------|
| `ContractResponse` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponseCatalog` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AccountRegistryDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.BookkeepingKernelDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.AuditDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.BookModelDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.CommitGuarantee` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.CurrencyDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ErrorDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.FailureCategory` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.FieldDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.InitializationRequirement` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.PlanExecutionDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.PreflightDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.RejectionDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ResponseDescriptorType` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ResponseModelDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `ContractResponse.ReversalDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates` |
| `PlanExecutionFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PlanFailurePolicy` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `PlanResultDetail` | `DOC_02_ProtocolAndDiscovery.md` | `PlanResultDetail` |
| `PlanTransactionMode` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `LedgerAssertion` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerAssertion` |
| `LedgerAssertionKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerBoundaryCheckpoint` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerExecutionJournal` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerFact` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerFact` |
| `LedgerFactKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerFactKind` |
| `LedgerJournalEntry` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerJournalKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerJournalStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerPlan` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlan` |
| `LedgerPlanFailure` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerPlanId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerPlanResult` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerPlanService` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanService` |
| `LedgerPlanStatus` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerPlanTransaction` | `DOC_03_BookSessionsAndAdapters.md` | `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `ReportingPeriodCloseStore`, And `LedgerPlanTransaction` |
| `LedgerStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStep` |
| `LedgerStepFailure` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerStepId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerStepKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerStepStatus` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
