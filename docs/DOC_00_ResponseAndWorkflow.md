---
afad: "5.0.1"
version: "0.63.0"
domain: INDEX_RESPONSE_AND_WORKFLOW
updated: "2026-08-20"
route:
  keywords: [fingrind, response, envelope, failure category, attestation diagnostics, ledger plan, workflow, execution journal]
  questions: ["where is the fingrind response contract documented", "which doc covers LedgerPlanFailure", "which doc covers ContractResponseCatalog", "where are attestation diagnostic descriptors documented"]
---

# Response And Workflow Index

**Purpose**: Route readers to public response-envelope and ledger-workflow contract symbols.
**Prerequisites**: Use [DOC_00_Index.md](./DOC_00_Index.md) for the complete bounded-context map.

| Symbol | File | Section |
|:-------|:-----|:--------|
| `ResponseDescriptorType` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractResponseCatalog` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ContractErrors` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailure` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailureDetails` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailurePaths` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `ContractFailureException` | `DOC_02_MachineContractAndDescriptors.md` | `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException` |
| `OpenBookFailureDetails` | `DOC_02_MachineContractAndDescriptors.md` | `OpenBookFailureDetails`, `OpenBookPreparationArtifactsRetained`, `RetainedOpenBookPreparationArtifact`, `OpenBookPreparationArtifactRole`, `OpenBookPublicationProgress`, And `OpenBookCompletionUncertain` |
| `OpenBookFailureDetails.OpenBookPreparationArtifactsRetained` | `DOC_02_MachineContractAndDescriptors.md` | `OpenBookFailureDetails`, `OpenBookPreparationArtifactsRetained`, `RetainedOpenBookPreparationArtifact`, `OpenBookPreparationArtifactRole`, `OpenBookPublicationProgress`, And `OpenBookCompletionUncertain` |
| `OpenBookFailureDetails.RetainedOpenBookPreparationArtifact` | `DOC_02_MachineContractAndDescriptors.md` | `OpenBookFailureDetails`, `OpenBookPreparationArtifactsRetained`, `RetainedOpenBookPreparationArtifact`, `OpenBookPreparationArtifactRole`, `OpenBookPublicationProgress`, And `OpenBookCompletionUncertain` |
| `OpenBookFailureDetails.OpenBookPreparationArtifactRole` | `DOC_02_MachineContractAndDescriptors.md` | `OpenBookFailureDetails`, `OpenBookPreparationArtifactsRetained`, `RetainedOpenBookPreparationArtifact`, `OpenBookPreparationArtifactRole`, `OpenBookPublicationProgress`, And `OpenBookCompletionUncertain` |
| `OpenBookFailureDetails.OpenBookPublicationProgress` | `DOC_02_MachineContractAndDescriptors.md` | `OpenBookFailureDetails`, `OpenBookPreparationArtifactsRetained`, `RetainedOpenBookPreparationArtifact`, `OpenBookPreparationArtifactRole`, `OpenBookPublicationProgress`, And `OpenBookCompletionUncertain` |
| `OpenBookFailureDetails.OpenBookCompletionUncertain` | `DOC_02_MachineContractAndDescriptors.md` | `OpenBookFailureDetails`, `OpenBookPreparationArtifactsRetained`, `RetainedOpenBookPreparationArtifact`, `OpenBookPreparationArtifactRole`, `OpenBookPublicationProgress`, And `OpenBookCompletionUncertain` |
| `AttestationDiagnosticDescriptors` | `DOC_02_MachineContractAndDescriptors.md` | `AttestationDiagnosticDescriptors` |
| `AttestationDiagnosticDescriptors.AdmissionContext` | `DOC_02_MachineContractAndDescriptors.md` | `AttestationDiagnosticDescriptors` |
| `AttestationDiagnosticDescriptors.DiagnosticDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `AttestationDiagnosticDescriptors` |
| `AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `AttestationDiagnosticDescriptors` |
| `AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `AttestationDiagnosticDescriptors` |
| `AccountRegistryDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `BookkeepingKernelDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `AuditDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `BookModelDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `CommitGuarantee` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `CurrencyDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ErrorDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `FailureCategory` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `FieldDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `InitializationRequirement` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `PlanAttestationOutcomeDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `PlanExecutionDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `PreflightDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `RejectionDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ResponseModelDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `ReversalDescriptor` | `DOC_02_MachineContractAndDescriptors.md` | `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates` |
| `PlanExecutionFacts` | `DOC_02_ProtocolAndDiscovery.md` | `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts` |
| `PlanFailurePolicy` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `PlanResultDetail` | `DOC_02_ProtocolAndDiscovery.md` | `PlanResultDetail` |
| `PlanTransactionMode` | `DOC_02_ProtocolAndDiscovery.md` | `PlanTransactionMode`, And `PlanFailurePolicy` |
| `LedgerAssertion` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerAssertion` |
| `LedgerAssertionKind` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerBoundaryCheckpoint` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerExecutionJournal` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerFact` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerFact` |
| `LedgerFactKind` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerFactKind` |
| `LedgerJournalEntry` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerJournalKind` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerJournalStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerPlan` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlan` |
| `LedgerPlanAttestationCommitMode` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerPlanAttestationDisposition`, `LedgerPlanAttestationCommitMode`, And `LedgerPlanAttestationCredentialMode` |
| `LedgerPlanAttestationCredentialMode` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerPlanAttestationDisposition`, `LedgerPlanAttestationCommitMode`, And `LedgerPlanAttestationCredentialMode` |
| `LedgerPlanAttestationDisposition` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerPlanAttestationDisposition`, `LedgerPlanAttestationCommitMode`, And `LedgerPlanAttestationCredentialMode` |
| `LedgerPlanFailure` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerPlanId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerPlanResult` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerPlanExecutionStore` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanTransaction`, `LedgerPlanMutationStore`, And `LedgerPlanExecutionStore` |
| `LedgerPlanMutationStore` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanTransaction`, `LedgerPlanMutationStore`, And `LedgerPlanExecutionStore` |
| `LedgerPlanReadOnlyExecutionStore` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanReadStore`, `LedgerPlanReadOnlyTransaction`, And `LedgerPlanReadOnlyExecutionStore` |
| `LedgerPlanReadOnlyTransaction` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanReadStore`, `LedgerPlanReadOnlyTransaction`, And `LedgerPlanReadOnlyExecutionStore` |
| `LedgerPlanReadStore` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanReadStore`, `LedgerPlanReadOnlyTransaction`, And `LedgerPlanReadOnlyExecutionStore` |
| `LedgerPlanTransaction` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanTransaction`, `LedgerPlanMutationStore`, And `LedgerPlanExecutionStore` |
| `LedgerPlanService` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanService` |
| `LedgerPlanReadOnlyService` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanReadOnlyService` |
| `LedgerPlanStatus` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `PlanAccountDeclarationService` | `DOC_02_PostingAndLedgerPlans.md` | `PlanAccountDeclarationService`, `PlanTaxRegistrationService`, `PlanPostingApplicationService`, And `PlanPostEntryOutcome` |
| `PlanPostEntryOutcome` | `DOC_02_PostingAndLedgerPlans.md` | `PlanAccountDeclarationService`, `PlanTaxRegistrationService`, `PlanPostingApplicationService`, And `PlanPostEntryOutcome` |
| `PlanPostingApplicationService` | `DOC_02_PostingAndLedgerPlans.md` | `PlanAccountDeclarationService`, `PlanTaxRegistrationService`, `PlanPostingApplicationService`, And `PlanPostEntryOutcome` |
| `PlanTaxRegistrationService` | `DOC_02_PostingAndLedgerPlans.md` | `PlanAccountDeclarationService`, `PlanTaxRegistrationService`, `PlanPostingApplicationService`, And `PlanPostEntryOutcome` |
| `LedgerStep` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerStep` |
| `LedgerStepFailure` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult` |
| `LedgerStepId` | `DOC_02_PostingAndLedgerPlans.md` | `LedgerPlanId` And `LedgerStepId` |
| `LedgerStepKind` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
| `LedgerStepStatus` | `DOC_02_LedgerPlanVocabulary.md` | `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus` |
