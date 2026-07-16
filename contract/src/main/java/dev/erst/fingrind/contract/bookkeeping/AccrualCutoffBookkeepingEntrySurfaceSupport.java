package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingOriginKind;

/** Entry-surface behavior owned by accrual cut-off bookkeeping-entry variants. */
final class AccrualCutoffBookkeepingEntrySurfaceSupport {
  private AccrualCutoffBookkeepingEntrySurfaceSupport() {}

  static BookkeepingEntryKind entryKind(AccrualCutoffBookkeepingEntryVariants entry) {
    return switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment _ -> BookkeepingEntryKind.PREPAYMENT;
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue _ ->
          BookkeepingEntryKind.DEFERRED_REVENUE;
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense _ ->
          BookkeepingEntryKind.ACCRUED_EXPENSE;
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition _ ->
          BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION;
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement _ ->
          BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT;
    };
  }

  static PostingOriginKind postingOriginKind(AccrualCutoffBookkeepingEntryVariants entry) {
    return switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment _ -> PostingOriginKind.PREPAYMENT;
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue _ ->
          PostingOriginKind.DEFERRED_REVENUE;
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense _ ->
          PostingOriginKind.ACCRUED_EXPENSE;
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition _ ->
          PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION;
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement _ ->
          PostingOriginKind.ACCRUED_EXPENSE_SETTLEMENT;
    };
  }

  static JournalEntry journalEntry(AccrualCutoffBookkeepingEntryVariants entry) {
    return switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
          BookkeepingEntrySupport.pairedEntry(
              prepayment.effectiveDate(),
              prepayment.prepaymentAssetAccountCode(),
              prepayment.cashAccountCode(),
              prepayment.amount());
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
          BookkeepingEntrySupport.pairedEntry(
              deferredRevenue.effectiveDate(),
              deferredRevenue.cashAccountCode(),
              deferredRevenue.deferredRevenueAccountCode(),
              deferredRevenue.amount());
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
          BookkeepingEntrySupport.pairedEntry(
              accruedExpense.effectiveDate(),
              accruedExpense.expenseAccountCode(),
              accruedExpense.accruedExpenseLiabilityAccountCode(),
              accruedExpense.amount());
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
          resolvedApplicationJournalEntry(
              recognition.effectiveDate(),
              recognition.amount(),
              recognition.resolvedApplication(),
              AccrualCutoffApplicationKind.RECOGNITION,
              "accrualCutoffRecognition");
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
          resolvedApplicationJournalEntry(
              settlement.effectiveDate(),
              settlement.amount(),
              settlement.resolvedApplication(),
              AccrualCutoffApplicationKind.SETTLEMENT,
              "accruedExpenseSettlement");
    };
  }

  private static JournalEntry resolvedApplicationJournalEntry(
      java.time.LocalDate effectiveDate,
      MonetaryAmount amount,
      @org.jspecify.annotations.Nullable ResolvedAccrualCutoffApplication resolvedApplication,
      AccrualCutoffApplicationKind expectedKind,
      String entryName) {
    ResolvedAccrualCutoffApplication requiredResolution =
        requireResolvedApplication(resolvedApplication, expectedKind, entryName);
    return BookkeepingEntrySupport.pairedEntry(
        effectiveDate,
        requiredResolution.debitAccountCode(),
        requiredResolution.creditAccountCode(),
        amount);
  }

  static ResolvedAccrualCutoffApplication requireResolvedApplication(
      @org.jspecify.annotations.Nullable ResolvedAccrualCutoffApplication resolvedApplication,
      AccrualCutoffApplicationKind expectedKind,
      String entryName) {
    if (resolvedApplication == null) {
      throw new IllegalStateException(
          entryName + " requires executor-resolved accrual cut-off facts.");
    }
    if (resolvedApplication.applicationKind() != expectedKind) {
      throw new IllegalStateException(
          entryName
              + " requires executor-resolved applicationKind "
              + expectedKind.wireValue()
              + ".");
    }
    return resolvedApplication;
  }
}
