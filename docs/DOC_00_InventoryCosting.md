---
afad: "5.0.1"
version: "0.63.0"
domain: INDEX
updated: "2026-08-20"
route:
  keywords: [fingrind, inventory, costing, weighted average, quantity, on-hand, carrying cost, movement, admission, rejection, readback]
  questions: ["where are inventory-costing symbols documented in fingrind", "which doc covers inventory admission and on-hand state", "where do inventory movement rejections route", "how does get-posting reconstruct derived inventory costing"]
---

# Inventory Costing API Index

**Purpose**: Route the inventory-costing subsystem's public symbols to their canonical reference
sections without widening the general API index.

## Symbol Routing

| Symbol | File | Section |
|:-------|:-----|:--------|
| `InventoryAccountState` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
| `InventoryAdmissionPolicy` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure` |
| `InventoryAdmissionPolicy.InventoryAdmissionFailure` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure` |
| `InventoryBookkeepingEntryVariants` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure` |
| `InventoryCostingDoctrine` | `DOC_01_Core.md` | `InventoryCostingDoctrine` |
| `InventoryEntrySemanticsViolations` | `DOC_02_PostingAndLedgerPlans.md` | `InventoryEntrySemanticsViolations` |
| `InventoryMovementLookupStore` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
| `InventoryMovementKind` | `DOC_01_Core.md` | `InventoryMovementKind` |
| `InventoryValuationAccount` | `DOC_02_InventoryValuation.md` | `InventoryValuationQuery`, `InventoryValuationAccount`, `InventoryValuationMovement`, `InventoryValuationReport`, `InventoryValuationResult`, `InventoryValuationCriteria`, And `InventoryValuationView` |
| `InventoryValuationCriteria` | `DOC_02_InventoryValuation.md` | `InventoryValuationQuery`, `InventoryValuationAccount`, `InventoryValuationMovement`, `InventoryValuationReport`, `InventoryValuationResult`, `InventoryValuationCriteria`, And `InventoryValuationView` |
| `InventoryValuationMovement` | `DOC_02_InventoryValuation.md` | `InventoryValuationQuery`, `InventoryValuationAccount`, `InventoryValuationMovement`, `InventoryValuationReport`, `InventoryValuationResult`, `InventoryValuationCriteria`, And `InventoryValuationView` |
| `InventoryValuationMovementRecord` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
| `InventoryValuationQuery` | `DOC_02_InventoryValuation.md` | `InventoryValuationQuery`, `InventoryValuationAccount`, `InventoryValuationMovement`, `InventoryValuationReport`, `InventoryValuationResult`, `InventoryValuationCriteria`, And `InventoryValuationView` |
| `InventoryValuationReport` | `DOC_02_InventoryValuation.md` | `InventoryValuationQuery`, `InventoryValuationAccount`, `InventoryValuationMovement`, `InventoryValuationReport`, `InventoryValuationResult`, `InventoryValuationCriteria`, And `InventoryValuationView` |
| `InventoryValuationReportModelBuilder` | `DOC_02_SharedReportModel.md` | `InventoryValuationReportModelBuilder` |
| `InventoryValuationResult` | `DOC_02_InventoryValuation.md` | `InventoryValuationQuery`, `InventoryValuationAccount`, `InventoryValuationMovement`, `InventoryValuationReport`, `InventoryValuationResult`, `InventoryValuationCriteria`, And `InventoryValuationView` |
| `InventoryValuationStore` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
| `InventoryValuationView` | `DOC_02_InventoryValuation.md` | `InventoryValuationQuery`, `InventoryValuationAccount`, `InventoryValuationMovement`, `InventoryValuationReport`, `InventoryValuationResult`, `InventoryValuationCriteria`, And `InventoryValuationView` |
| `InventoryMovementPrecedesAccountHorizon` | `DOC_02_PostingAndLedgerPlans.md` | `InventoryMovementPrecedesAccountHorizon`, `InventoryQuantityBelowZero`, And `InventoryWriteDownExceedsCarryingCost` |
| `InventoryMovementPrecedesAccountHorizonViolation` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
| `InventoryMovementRecord` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
| `InventoryPostingResolution` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure` |
| `InventoryQuantityBelowZero` | `DOC_02_PostingAndLedgerPlans.md` | `InventoryMovementPrecedesAccountHorizon`, `InventoryQuantityBelowZero`, And `InventoryWriteDownExceedsCarryingCost` |
| `InventoryQuantityBelowZeroViolation` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
| `InventoryStateLookupStore` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
| `InventoryWriteDownExceedsCarryingCost` | `DOC_02_PostingAndLedgerPlans.md` | `InventoryMovementPrecedesAccountHorizon`, `InventoryQuantityBelowZero`, And `InventoryWriteDownExceedsCarryingCost` |
| `InventoryWriteDownExceedsCarryingCostViolation` | `DOC_03_BookSessionsAndAdapters.md` | `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation` |
