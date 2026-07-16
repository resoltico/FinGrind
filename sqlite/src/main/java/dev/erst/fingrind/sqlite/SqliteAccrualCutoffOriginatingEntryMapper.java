package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Rebuilds caller-authored accrual cut-off facts from their dedicated durable aggregate tables. */
final class SqliteAccrualCutoffOriginatingEntryMapper {
  private SqliteAccrualCutoffOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingOriginKind postingOriginKind) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(postingId, "postingId");
    return switch (postingOriginKind) {
      case PREPAYMENT, DEFERRED_REVENUE, ACCRUED_EXPENSE ->
          originatingCutoff(activeDatabase, postingId, postingRow, journalEntry, postingOriginKind);
      case ACCRUAL_CUTOFF_RECOGNITION, ACCRUED_EXPENSE_SETTLEMENT ->
          originatingApplication(
              activeDatabase, postingId, postingRow, journalEntry, postingOriginKind);
      default -> null;
    };
  }

  private static BookkeepingEntry originatingCutoff(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingOriginKind postingOriginKind) {
    AccrualCutoffRecord cutoff =
        SqliteAccrualCutoffStatementQueries.findCutoffByOriginPosting(activeDatabase, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Accrual-cutoff origin posting has no matching durable cut-off aggregate."));
    requireOriginKindMatches(cutoff, postingOriginKind);
    return originEntry(cutoff, postingRow, journalEntry);
  }

  private static BookkeepingEntry originatingApplication(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingOriginKind postingOriginKind) {
    Optional<SqliteAccrualCutoffStatementQueries.ApplicationContext> application =
        SqliteAccrualCutoffStatementQueries.findApplicationContext(activeDatabase, postingId);
    if (application.isEmpty()) {
      throw new IllegalStateException(
          "Accrual-cutoff application posting has no matching durable application record.");
    }
    SqliteAccrualCutoffStatementQueries.ApplicationContext context = application.orElseThrow();
    MonetaryAmount amount =
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow);
    return switch (context.applicationKind()) {
      case RECOGNITION -> {
        if (postingOriginKind != PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION) {
          throw new IllegalStateException(
              "Accrual-cutoff recognition application does not match the posting origin.");
        }
        yield new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
            journalEntry.effectiveDate(), context.accrualCutoffId(), amount, null);
      }
      case SETTLEMENT -> {
        if (postingOriginKind != PostingOriginKind.ACCRUED_EXPENSE_SETTLEMENT) {
          throw new IllegalStateException(
              "Accrued-expense settlement application does not match the posting origin.");
        }
        yield new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
            journalEntry.effectiveDate(),
            context.accrualCutoffId(),
            SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(
                postingRow),
            amount,
            null);
      }
    };
  }

  private static void requireOriginKindMatches(
      AccrualCutoffRecord cutoff, PostingOriginKind postingOriginKind) {
    PostingOriginKind expectedOriginKind =
        switch (cutoff) {
          case AccrualCutoffRecord.Prepayment ignored -> PostingOriginKind.PREPAYMENT;
          case AccrualCutoffRecord.DeferredRevenue ignored -> PostingOriginKind.DEFERRED_REVENUE;
          case AccrualCutoffRecord.AccruedExpense ignored -> PostingOriginKind.ACCRUED_EXPENSE;
        };
    if (postingOriginKind != expectedOriginKind) {
      throw new IllegalStateException(
          "Accrual-cutoff aggregate kind does not match the persisted posting origin.");
    }
  }

  private static BookkeepingEntry originEntry(
      AccrualCutoffRecord cutoff, SqliteNativeStatement postingRow, JournalEntry journalEntry) {
    MonetaryAmount amount =
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow);
    return switch (cutoff) {
      case AccrualCutoffRecord.Prepayment prepayment ->
          new AccrualCutoffBookkeepingEntryVariants.Prepayment(
              journalEntry.effectiveDate(),
              prepayment.accrualCutoffId(),
              prepayment.prepaymentAssetAccountCode(),
              prepayment.expenseAccountCode(),
              SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(
                  postingRow),
              amount,
              prepayment.recognitionInterval());
      case AccrualCutoffRecord.DeferredRevenue deferredRevenue ->
          new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
              journalEntry.effectiveDate(),
              deferredRevenue.accrualCutoffId(),
              SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(
                  postingRow),
              deferredRevenue.deferredRevenueAccountCode(),
              deferredRevenue.revenueAccountCode(),
              amount,
              deferredRevenue.recognitionInterval());
      case AccrualCutoffRecord.AccruedExpense accruedExpense ->
          new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
              journalEntry.effectiveDate(),
              accruedExpense.accrualCutoffId(),
              accruedExpense.expenseAccountCode(),
              accruedExpense.accruedExpenseLiabilityAccountCode(),
              amount);
    };
  }
}
