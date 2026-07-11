package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Rebuilds non-inventory operational entries and reversal lineage from persisted facts. */
final class SqliteRoutineOriginatingEntryMapper {
  private static final Map<PostingOriginKind, SqlitePostingOriginatingEntryBuilder> ENTRY_BUILDERS =
      Map.ofEntries(
          Map.entry(
              PostingOriginKind.DIRECT_JOURNAL,
              SqliteRoutineOriginatingEntryMapper::directJournalEntry),
          Map.entry(
              PostingOriginKind.EXPENSE_SETTLED,
              SqliteRoutineOriginatingEntryMapper::expenseSettledEntry),
          Map.entry(
              PostingOriginKind.EXPENSE_ON_CREDIT,
              SqliteRoutineOriginatingEntryMapper::expenseOnCreditEntry),
          Map.entry(PostingOriginKind.RECEIPT, SqliteRoutineOriginatingEntryMapper::receiptEntry),
          Map.entry(PostingOriginKind.PAYMENT, SqliteRoutineOriginatingEntryMapper::paymentEntry),
          Map.entry(
              PostingOriginKind.OWNER_CONTRIBUTION,
              SqliteRoutineOriginatingEntryMapper::ownerContributionEntry),
          Map.entry(
              PostingOriginKind.OWNER_WITHDRAWAL,
              SqliteRoutineOriginatingEntryMapper::ownerWithdrawalEntry),
          Map.entry(
              PostingOriginKind.REVERSAL, SqliteRoutineOriginatingEntryMapper::reversalEntry));

  private SqliteRoutineOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      PostingOriginKind postingOriginKind,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    SqlitePostingOriginatingEntryBuilder builder = ENTRY_BUILDERS.get(postingOriginKind);
    return builder == null
        ? null
        : builder.build(
            postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails);
  }

  private static BookkeepingEntry directJournalEntry(
      SqliteNativeStatement ignoredPostingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.DirectJournal(journalEntry, foreignExchangeDetails);
  }

  private static BookkeepingEntry expenseSettledEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.ExpenseSettled(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry expenseOnCreditEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.ExpenseOnCredit(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry receiptEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails ignoredForeignExchangeDetails) {
    return new BookkeepingEntry.Receipt(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.settlementAdjunct(postingRow));
  }

  private static BookkeepingEntry paymentEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails ignoredForeignExchangeDetails) {
    return new BookkeepingEntry.Payment(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.settlementAdjunct(postingRow));
  }

  private static BookkeepingEntry ownerContributionEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.OwnerContribution(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        foreignExchangeDetails);
  }

  private static BookkeepingEntry ownerWithdrawalEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.OwnerWithdrawal(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        foreignExchangeDetails);
  }

  private static BookkeepingEntry reversalEntry(
      SqliteNativeStatement ignoredPostingRow,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    if (!(postingLineage instanceof PostingLineageModel.Reversal reversal)) {
      throw new IllegalStateException(
          "Persisted reversal posting is missing reversal lineage details.");
    }
    return new BookkeepingEntry.Reversal(
        journalEntry.effectiveDate(),
        new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
            reversal.reference(), reversal.reason()),
        foreignExchangeDetails,
        journalEntry);
  }
}
