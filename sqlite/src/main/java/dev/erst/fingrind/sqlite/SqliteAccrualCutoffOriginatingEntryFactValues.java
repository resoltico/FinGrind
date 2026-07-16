package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import org.jspecify.annotations.Nullable;

/** Maps resolved accrual cut-off entry facts to the retained posting-fact scalar columns. */
final class SqliteAccrualCutoffOriginatingEntryFactValues {
  private SqliteAccrualCutoffOriginatingEntryFactValues() {}

  static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues originatingEntryFactValues(
      AccrualCutoffBookkeepingEntryVariants entry) {
    return switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              prepayment.prepaymentAssetAccountCode().value(),
              prepayment.cashAccountCode().value(),
              prepayment.amount(),
              null);
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              deferredRevenue.cashAccountCode().value(),
              deferredRevenue.deferredRevenueAccountCode().value(),
              deferredRevenue.amount(),
              null);
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              accruedExpense.expenseAccountCode().value(),
              accruedExpense.accruedExpenseLiabilityAccountCode().value(),
              accruedExpense.amount(),
              null);
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
          resolvedApplicationFactValues(
              recognition.amount(), recognition.resolvedApplication(), "accrualCutoffRecognition");
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
          resolvedApplicationFactValues(
              settlement.amount(), settlement.resolvedApplication(), "accruedExpenseSettlement");
    };
  }

  private static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues
      resolvedApplicationFactValues(
          dev.erst.fingrind.contract.bookkeeping.MonetaryAmount amount,
          @Nullable ResolvedAccrualCutoffApplication resolvedApplication,
          String entryName) {
    if (resolvedApplication == null) {
      throw new IllegalStateException(entryName + " requires executor-resolved cut-off facts.");
    }
    return SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
        resolvedApplication.debitAccountCode().value(),
        resolvedApplication.creditAccountCode().value(),
        amount,
        null);
  }
}
