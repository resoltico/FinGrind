---
afad: "5.0.1"
version: "0.62.2"
domain: INDEX_POSTING_AND_REJECTIONS
updated: "2026-08-09"
route:
  keywords: [fingrind, posting, preflight, commit, rejection, idempotency, book start, reversal, translator, ledger-plan]
  questions: ["where are FinGrind posting rejection types documented", "which index routes PostingApplicationService", "where is the immutable book-start posting refusal documented"]
---

# Posting And Rejection Index

**Purpose**: Route posting command, local rejection, and published rejection symbols to their
bounded reference sections.
**Prerequisites**: Use [DOC_00_Index.md](./DOC_00_Index.md) for the complete bounded-context map.

| Symbol | File | Section |
|:-------|:-----|:--------|
| `BookkeepingAdministrationRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `PostingAcceptancePolicy.Decision`, `BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`, `BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingAdministrationRejectionPublishedMapper` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `PostingAcceptancePolicy.Decision`, `BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`, `BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingEntry` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingEntry`, `TypedBookkeepingEntry`, `BookkeepingEntrySurface`, And `BookkeepingEntryKind` |
| `BookkeepingEntrySurface` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingEntry`, `TypedBookkeepingEntry`, `BookkeepingEntrySurface`, And `BookkeepingEntryKind` |
| `BookkeepingEntryKind` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingEntry`, `TypedBookkeepingEntry`, `BookkeepingEntrySurface`, And `BookkeepingEntryKind` |
| `BookkeepingEntry.OpeningPosition.OpeningAccountBalance` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingEntry`, `TypedBookkeepingEntry`, `BookkeepingEntrySurface`, And `BookkeepingEntryKind` |
| `TypedBookkeepingEntry` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingEntry`, `TypedBookkeepingEntry`, `BookkeepingEntrySurface`, And `BookkeepingEntryKind` |
| `BookkeepingAccountSemanticsViolations` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingAccountSemanticsViolations`, `BookkeepingEvidenceSemanticsViolations`, `BookkeepingEntryModeSemanticsViolations`, And `BookkeepingTaxSemanticsViolations` |
| `BookkeepingEvidenceSemanticsViolations` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingAccountSemanticsViolations`, `BookkeepingEvidenceSemanticsViolations`, `BookkeepingEntryModeSemanticsViolations`, And `BookkeepingTaxSemanticsViolations` |
| `BookkeepingEntryModeSemanticsViolations` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingAccountSemanticsViolations`, `BookkeepingEvidenceSemanticsViolations`, `BookkeepingEntryModeSemanticsViolations`, And `BookkeepingTaxSemanticsViolations` |
| `BookkeepingPostingEffectiveDateBeforeBookStart` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingPostingEffectiveDateBeforeBookStart` |
| `BookkeepingPostingRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `PostingAcceptancePolicy.Decision`, `BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`, `BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingRequestPublishedLanguageTranslator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `PostingAcceptancePolicy.Decision`, `BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`, `BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingPublishedLanguageTranslator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `PostingAcceptancePolicy.Decision`, `BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`, `BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, And `BookkeepingPublishedLanguageTranslator` |
| `BookkeepingTaxSemanticsViolations` | `DOC_02_PostingAndLedgerPlans.md` | `BookkeepingAccountSemanticsViolations`, `BookkeepingEvidenceSemanticsViolations`, `BookkeepingEntryModeSemanticsViolations`, And `BookkeepingTaxSemanticsViolations` |
| `CommitEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `AttestationCommit` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PostEntryCommand` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryCommand` |
| `PostEntryCommandTranslator` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryCommandTranslator` |
| `PostEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `PostingAcceptancePolicy` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `PostingAcceptancePolicy.Decision`, `BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`, `BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, And `BookkeepingPublishedLanguageTranslator` |
| `PostingAcceptancePolicy.Decision` | `DOC_02_PostingAndLedgerPlans.md` | `PostingAcceptancePolicy`, `PostingAcceptancePolicy.Decision`, `BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`, `BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, And `BookkeepingPublishedLanguageTranslator` |
| `PostingApplicationService` | `DOC_02_PostingAndLedgerPlans.md` | `PostingApplicationService` |
| `PostingPreflightService` | `DOC_02_PostingAndLedgerPlans.md` | `PostingPreflightService` |
| `PlanPostingApplicationService` | `DOC_02_PostingAndLedgerPlans.md` | `PlanAccountDeclarationService`, `PlanTaxRegistrationService`, `PlanPostingApplicationService`, And `PlanPostEntryOutcome` |
| `PlanPostEntryOutcome` | `DOC_02_PostingAndLedgerPlans.md` | `PlanAccountDeclarationService`, `PlanTaxRegistrationService`, `PlanPostingApplicationService`, And `PlanPostEntryOutcome` |
| `PostingCommand` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, `PostingOriginatingEntryValidator`, And `PostingRequestModel` |
| `PostingDraft` | `DOC_02_PostingAndLedgerPlans.md` | `PostingDraft` |
| `PostingIdGenerator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingIdGenerator` |
| `PostingLineage` | `DOC_02_PostingAndLedgerPlans.md` | `PostingLineage` |
| `PostingLineageModel` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, `PostingOriginatingEntryValidator`, And `PostingRequestModel` |
| `PostingOriginatingEntryValidator` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, `PostingOriginatingEntryValidator`, And `PostingRequestModel` |
| `PostingEffectiveDateBeforeBookStart` | `DOC_02_PostingAndLedgerPlans.md` | `PostingEffectiveDateBeforeBookStart` |
| `PostingRejection` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection`, `PostingInventoryRejectionSemantics`, And `PostingRejectionSemantics` |
| `PostingInventoryRejectionSemantics` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection`, `PostingInventoryRejectionSemantics`, And `PostingRejectionSemantics` |
| `PostingRejectionSemantics` | `DOC_02_PostingAndLedgerPlans.md` | `PostingRejection`, `PostingInventoryRejectionSemantics`, And `PostingRejectionSemantics` |
| `PostingRequestModel` | `DOC_02_PostingAndLedgerPlans.md` | `PostingCommand`, `PostingLineageModel`, `PostingOriginatingEntryValidator`, And `PostingRequestModel` |
| `PreflightEntryResult` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `UuidV7PostingIdGenerator` | `DOC_02_PostingAndLedgerPlans.md` | `UuidV7PostingIdGenerator` |
