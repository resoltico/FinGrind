package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Accrual cut-off request-template scaffolds owned by the accrual cut-off context. */
final class MachineContractAccrualCutoffPostEntryVariantTemplates {
  private MachineContractAccrualCutoffPostEntryVariantTemplates() {}

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor prepaymentTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.accrualCutoffTemplate(
        BookkeepingEntryKind.PREPAYMENT,
        "cash",
        null,
        "operating-expense",
        "prepayment-2026-q1",
        "prepaid-expense",
        null,
        null,
        new ContractTemplates.RecognitionIntervalTemplateDescriptor("2026-01-15", "2026-03-31"));
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor deferredRevenueTemplate(
      @Nullable BookTemplateId bookTemplateId) {
    return MachineContractPostEntryVariantTemplates.accrualCutoffTemplate(
        BookkeepingEntryKind.DEFERRED_REVENUE,
        "cash",
        MachineContractPostEntryVariantTemplates.salesRevenueAccountCode(bookTemplateId),
        null,
        "deferred-revenue-2026-q1",
        null,
        "deferred-revenue",
        null,
        new ContractTemplates.RecognitionIntervalTemplateDescriptor("2026-01-15", "2026-03-31"));
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor accruedExpenseTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.accrualCutoffTemplate(
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        null,
        null,
        "operating-expense",
        "accrued-expense-2026-01",
        null,
        null,
        "accrued-expense",
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      accrualCutoffRecognitionTemplate(@Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.accrualCutoffTemplate(
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        null,
        null,
        null,
        "prepayment-2026-q1",
        null,
        null,
        null,
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      accruedExpenseSettlementTemplate(@Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.accrualCutoffTemplate(
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
        "cash",
        null,
        null,
        "accrued-expense-2026-01",
        null,
        null,
        null,
        null);
  }
}
