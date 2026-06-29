package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import org.jspecify.annotations.Nullable;

/** Rebuilds the published caller-authored entry from persisted posting-side SQLite facts. */
final class SqlitePostingOriginatingEntryMapper {
  private SqlitePostingOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingOriginKind postingOriginKind,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return switch (postingOriginKind) {
      case DIRECT_JOURNAL ->
          new BookkeepingEntry.DirectJournal(journalEntry, foreignExchangeDetails);
      case SALE ->
          new BookkeepingEntry.Sale(
              journalEntry.effectiveDate(),
              new AccountCode(
                  SqlitePostingMapper.requiredText(
                      postingRow, SqlitePostingColumnIndexes.COL_ENTRY_CASH_ACCOUNT_CODE)),
              new AccountCode(
                  SqlitePostingMapper.requiredText(
                      postingRow, SqlitePostingColumnIndexes.COL_ENTRY_REVENUE_ACCOUNT_CODE)),
              requiredEntryAmount(postingRow),
              foreignExchangeDetails,
              taxSelection(appliedTax),
              appliedTax);
      case EXPENSE ->
          new BookkeepingEntry.Expense(
              journalEntry.effectiveDate(),
              new AccountCode(
                  SqlitePostingMapper.requiredText(
                      postingRow, SqlitePostingColumnIndexes.COL_ENTRY_EXPENSE_ACCOUNT_CODE)),
              new AccountCode(
                  SqlitePostingMapper.requiredText(
                      postingRow, SqlitePostingColumnIndexes.COL_ENTRY_CASH_ACCOUNT_CODE)),
              requiredEntryAmount(postingRow),
              foreignExchangeDetails,
              taxSelection(appliedTax),
              appliedTax);
      case OWNER_CONTRIBUTION ->
          new BookkeepingEntry.OwnerContribution(
              journalEntry.effectiveDate(),
              new AccountCode(
                  SqlitePostingMapper.requiredText(
                      postingRow, SqlitePostingColumnIndexes.COL_ENTRY_CASH_ACCOUNT_CODE)),
              new AccountCode(
                  SqlitePostingMapper.requiredText(
                      postingRow, SqlitePostingColumnIndexes.COL_ENTRY_EQUITY_ACCOUNT_CODE)),
              requiredEntryAmount(postingRow),
              foreignExchangeDetails);
      case OWNER_WITHDRAWAL ->
          new BookkeepingEntry.OwnerWithdrawal(
              journalEntry.effectiveDate(),
              new AccountCode(
                  SqlitePostingMapper.requiredText(
                      postingRow, SqlitePostingColumnIndexes.COL_ENTRY_EQUITY_ACCOUNT_CODE)),
              new AccountCode(
                  SqlitePostingMapper.requiredText(
                      postingRow, SqlitePostingColumnIndexes.COL_ENTRY_CASH_ACCOUNT_CODE)),
              requiredEntryAmount(postingRow),
              foreignExchangeDetails);
      case OPENING_POSITION ->
          new BookkeepingEntry.OpeningPosition(
              journalEntry.effectiveDate(),
              journalEntry.lines().stream()
                  .map(
                      line ->
                          new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                              line.accountCode(),
                              line.side(),
                              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                                  line.amount().money())))
                  .toList());
      case REVERSAL -> {
        if (!(postingLineage instanceof PostingLineageModel.Reversal reversal)) {
          throw new IllegalStateException(
              "Persisted reversal posting is missing reversal lineage details.");
        }
        yield new BookkeepingEntry.Reversal(
            journalEntry,
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                reversal.reference(), reversal.reason()),
            foreignExchangeDetails);
      }
      case INTERIM_RESULT_SWEEP, FISCAL_YEAR_CLOSE -> null;
    };
  }

  private static @Nullable TaxSelection taxSelection(@Nullable AppliedTax appliedTax) {
    if (appliedTax == null) {
      return null;
    }
    return new TaxSelection(appliedTax.taxRegistrationId(), appliedTax.taxCode());
  }

  private static dev.erst.fingrind.contract.bookkeeping.MonetaryAmount requiredEntryAmount(
      SqliteNativeStatement postingRow) {
    return dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
        Money.ofMinorUnits(
            dev.erst.fingrind.core.CurrencyUnit.of(
                SqlitePostingMapper.requiredText(
                    postingRow, SqlitePostingColumnIndexes.COL_ENTRY_AMOUNT_CURRENCY_CODE)),
            postingRow.columnLong(SqlitePostingColumnIndexes.COL_ENTRY_AMOUNT_MINOR)));
  }
}
