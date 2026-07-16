package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Persists accrued cut-off aggregates and lifecycle applications in the posting transaction. */
final class SqliteAccrualCutoffWriter {
  private SqliteAccrualCutoffWriter() {}

  static void persist(SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(postingFact, "postingFact");
    BookkeepingEntry callerAuthoredEntry = postingFact.callerAuthoredEntry().orElse(null);
    if (callerAuthoredEntry == null) {
      return;
    }
    switch (callerAuthoredEntry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
          insertPrepayment(activeDatabase, postingFact, prepayment);
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
          insertDeferredRevenue(activeDatabase, postingFact, deferredRevenue);
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
          insertAccruedExpense(activeDatabase, postingFact, accruedExpense);
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
          insertApplication(
              activeDatabase,
              postingFact,
              recognition.accrualCutoffId(),
              recognition.amount(),
              "RECOGNITION");
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
          insertApplication(
              activeDatabase,
              postingFact,
              settlement.accrualCutoffId(),
              settlement.amount(),
              "SETTLEMENT");
      case BookkeepingEntry.Reversal _ -> insertReversalCompensation(activeDatabase, postingFact);
      default -> {}
    }
  }

  private static void insertPrepayment(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment) {
    insertCutoff(
        activeDatabase,
        postingFact,
        new CutoffWrite(
            prepayment.accrualCutoffId().value(),
            AccrualCutoffKind.PREPAYMENT,
            prepayment.effectiveDate(),
            prepayment.prepaymentAssetAccountCode().value(),
            prepayment.expenseAccountCode().value(),
            prepayment.amount(),
            prepayment.recognitionInterval().startDate(),
            prepayment.recognitionInterval().endDate()));
  }

  private static void insertDeferredRevenue(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue) {
    insertCutoff(
        activeDatabase,
        postingFact,
        new CutoffWrite(
            deferredRevenue.accrualCutoffId().value(),
            AccrualCutoffKind.DEFERRED_REVENUE,
            deferredRevenue.effectiveDate(),
            deferredRevenue.deferredRevenueAccountCode().value(),
            deferredRevenue.revenueAccountCode().value(),
            deferredRevenue.amount(),
            deferredRevenue.recognitionInterval().startDate(),
            deferredRevenue.recognitionInterval().endDate()));
  }

  private static void insertAccruedExpense(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense) {
    insertCutoff(
        activeDatabase,
        postingFact,
        new CutoffWrite(
            accruedExpense.accrualCutoffId().value(),
            AccrualCutoffKind.ACCRUED_EXPENSE,
            accruedExpense.effectiveDate(),
            accruedExpense.accruedExpenseLiabilityAccountCode().value(),
            accruedExpense.expenseAccountCode().value(),
            accruedExpense.amount(),
            null,
            null));
  }

  private static void insertCutoff(
      SqliteNativeDatabase activeDatabase, CommittedPosting postingFact, CutoffWrite cutoff) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAccrualCutoffSql.INSERT_CUTOFF)) {
      statement.bindText(1, cutoff.accrualCutoffId());
      statement.bindText(2, cutoff.kind().wireValue());
      statement.bindText(3, postingFact.postingId().value());
      statement.bindText(4, CanonicalTemporalText.formatLocalDate(cutoff.originatedOn()));
      statement.bindText(5, cutoff.cutoffAccountCode());
      statement.bindText(6, cutoff.recognitionAccountCode());
      statement.bindText(7, cutoff.amount().currencyCode());
      statement.bindLong(8, Long.parseLong(cutoff.amount().minorUnits()));
      bindOptionalDate(statement, 9, cutoff.recognitionStartDate());
      bindOptionalDate(statement, 10, cutoff.recognitionEndDate());
      statement.step();
    }
  }

  private static void insertApplication(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId accrualCutoffId,
      dev.erst.fingrind.contract.bookkeeping.MonetaryAmount amount,
      String applicationKind) {
    insertApplication(
        activeDatabase,
        postingFact,
        accrualCutoffId.value(),
        amount.currencyCode(),
        Long.parseLong(amount.minorUnits()),
        applicationKind);
  }

  private static void insertReversalCompensation(
      SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
    dev.erst.fingrind.core.PostingId priorPostingId =
        postingFact.reversalReference().orElseThrow().priorPostingId();
    var origin =
        SqliteAccrualCutoffStatementQueries.findCutoffByOriginPosting(
            activeDatabase, priorPostingId);
    if (origin.isPresent()) {
      var cutoff = origin.orElseThrow();
      insertApplication(
          activeDatabase,
          postingFact,
          cutoff.accrualCutoffId().value(),
          cutoff.originalAmount().currencyUnit().code(),
          cutoff.originalAmount().minorUnits(),
          "ORIGIN_REVERSAL");
      return;
    }
    SqliteAccrualCutoffStatementQueries.findApplicationReversalInput(activeDatabase, priorPostingId)
        .ifPresent(
            input ->
                insertApplication(
                    activeDatabase,
                    postingFact,
                    input.accrualCutoffId().value(),
                    input.amount().currencyUnit().code(),
                    Math.negateExact(input.amount().minorUnits()),
                    "APPLICATION_REVERSAL"));
  }

  private static void insertApplication(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      String accrualCutoffId,
      String currencyCode,
      long amountMinor,
      String applicationKind) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAccrualCutoffSql.INSERT_APPLICATION)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, accrualCutoffId);
      statement.bindText(3, applicationKind);
      statement.bindText(
          4, CanonicalTemporalText.formatLocalDate(postingFact.journalEntry().effectiveDate()));
      statement.bindText(5, currencyCode);
      statement.bindLong(6, amountMinor);
      statement.step();
    }
  }

  private static void bindOptionalDate(
      SqliteNativeStatement statement, int parameterIndex, @Nullable LocalDate value) {
    if (value == null) {
      statement.bindNull(parameterIndex);
      return;
    }
    statement.bindText(parameterIndex, CanonicalTemporalText.formatLocalDate(value));
  }

  /** One typed accrual-cutoff aggregate fact ready for durable persistence. */
  private record CutoffWrite(
      String accrualCutoffId,
      AccrualCutoffKind kind,
      LocalDate originatedOn,
      String cutoffAccountCode,
      String recognitionAccountCode,
      MonetaryAmount amount,
      @Nullable LocalDate recognitionStartDate,
      @Nullable LocalDate recognitionEndDate) {}
}
