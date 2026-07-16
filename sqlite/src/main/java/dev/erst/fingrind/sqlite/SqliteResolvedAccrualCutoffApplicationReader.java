package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Rebuilds executor-resolved cut-off application facts from durable aggregate state. */
final class SqliteResolvedAccrualCutoffApplicationReader {
  private SqliteResolvedAccrualCutoffApplicationReader() {}

  static @Nullable BookkeepingEntry resolve(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      @Nullable BookkeepingEntry callerAuthoredEntry) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(postingId, "postingId");
    return switch (callerAuthoredEntry) {
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
          resolvedRecognition(activeDatabase, postingId, recognition);
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
          resolvedSettlement(activeDatabase, postingId, settlement);
      case null, default -> null;
    };
  }

  private static @Nullable BookkeepingEntry resolvedRecognition(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition) {
    SqliteAccrualCutoffStatementQueries.ApplicationContext context =
        SqliteAccrualCutoffStatementQueries.findApplicationContext(activeDatabase, postingId)
            .orElse(null);
    if (context == null || context.applicationKind() != AccrualCutoffApplicationKind.RECOGNITION) {
      return null;
    }
    AccrualCutoffRecord cutoff =
        SqliteAccrualCutoffStatementQueries.findCutoff(activeDatabase, context.accrualCutoffId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Accrual-cutoff recognition references a missing durable aggregate."));
    return switch (cutoff) {
      case AccrualCutoffRecord.Prepayment prepayment ->
          new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
              recognition.effectiveDate(),
              recognition.accrualCutoffId(),
              recognition.amount(),
              new ResolvedAccrualCutoffApplication(
                  prepayment.kind(),
                  AccrualCutoffApplicationKind.RECOGNITION,
                  prepayment.expenseAccountCode(),
                  prepayment.prepaymentAssetAccountCode()));
      case AccrualCutoffRecord.DeferredRevenue deferredRevenue ->
          new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
              recognition.effectiveDate(),
              recognition.accrualCutoffId(),
              recognition.amount(),
              new ResolvedAccrualCutoffApplication(
                  deferredRevenue.kind(),
                  AccrualCutoffApplicationKind.RECOGNITION,
                  deferredRevenue.deferredRevenueAccountCode(),
                  deferredRevenue.revenueAccountCode()));
      case AccrualCutoffRecord.AccruedExpense _ ->
          throw new IllegalStateException(
              "Accrued expenses do not admit recognition applications.");
    };
  }

  private static @Nullable BookkeepingEntry resolvedSettlement(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement) {
    SqliteAccrualCutoffStatementQueries.ApplicationContext context =
        SqliteAccrualCutoffStatementQueries.findApplicationContext(activeDatabase, postingId)
            .orElse(null);
    if (context == null || context.applicationKind() != AccrualCutoffApplicationKind.SETTLEMENT) {
      return null;
    }
    AccrualCutoffRecord cutoff =
        SqliteAccrualCutoffStatementQueries.findCutoff(activeDatabase, context.accrualCutoffId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Accrued-expense settlement references a missing durable aggregate."));
    if (!(cutoff instanceof AccrualCutoffRecord.AccruedExpense accruedExpense)) {
      throw new IllegalStateException("Only accrued expenses admit settlement applications.");
    }
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
        settlement.effectiveDate(),
        settlement.accrualCutoffId(),
        settlement.cashAccountCode(),
        settlement.amount(),
        new ResolvedAccrualCutoffApplication(
            accruedExpense.kind(),
            AccrualCutoffApplicationKind.SETTLEMENT,
            accruedExpense.accruedExpenseLiabilityAccountCode(),
            settlement.cashAccountCode()));
  }
}
