package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Rebuilds the published caller-authored entry from persisted posting-side SQLite facts. */
final class SqlitePostingOriginatingEntryMapper {
  /** Functional entry rebuilder owned by one persisted posting-origin kind. */
  @FunctionalInterface
  private interface OriginatingEntryBuilder {
    /** Rebuilds one published caller-authored entry from persisted posting-side facts. */
    BookkeepingEntry build(
        SqliteNativeStatement postingRow,
        JournalEntry journalEntry,
        PostingLineageModel postingLineage,
        @Nullable AppliedTax appliedTax,
        @Nullable ForeignExchangeDetails foreignExchangeDetails);
  }

  private static final Map<PostingOriginKind, OriginatingEntryBuilder> ENTRY_BUILDERS =
      entryBuilders();

  private SqlitePostingOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingOriginKind postingOriginKind,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    OriginatingEntryBuilder builder =
        ENTRY_BUILDERS.get(Objects.requireNonNull(postingOriginKind, "postingOriginKind"));
    if (builder == null) {
      return null;
    }
    return builder.build(
        postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails);
  }

  private static Map<PostingOriginKind, OriginatingEntryBuilder> entryBuilders() {
    return Map.ofEntries(
        Map.entry(
            PostingOriginKind.DIRECT_JOURNAL,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.DirectJournal(journalEntry, foreignExchangeDetails)),
        Map.entry(
            PostingOriginKind.SALE_SETTLED,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.SaleSettled(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    inventoryRelief(journalEntry, postingRow, appliedTax),
                    foreignExchangeDetails,
                    taxSelection(appliedTax),
                    appliedTax)),
        Map.entry(
            PostingOriginKind.SALE_ON_CREDIT,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.SaleOnCredit(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    inventoryRelief(journalEntry, postingRow, appliedTax),
                    taxSelection(appliedTax),
                    appliedTax)),
        Map.entry(
            PostingOriginKind.PURCHASE_SETTLED,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.PurchaseSettled(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    foreignExchangeDetails)),
        Map.entry(
            PostingOriginKind.PURCHASE_ON_CREDIT,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.PurchaseOnCredit(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow))),
        Map.entry(
            PostingOriginKind.EXPENSE_SETTLED,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.ExpenseSettled(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    foreignExchangeDetails,
                    taxSelection(appliedTax),
                    appliedTax)),
        Map.entry(
            PostingOriginKind.EXPENSE_ON_CREDIT,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.ExpenseOnCredit(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    taxSelection(appliedTax),
                    appliedTax)),
        Map.entry(
            PostingOriginKind.RECEIPT,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.Receipt(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    settlementAdjunct(postingRow))),
        Map.entry(
            PostingOriginKind.PAYMENT,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.Payment(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    settlementAdjunct(postingRow))),
        Map.entry(
            PostingOriginKind.OWNER_CONTRIBUTION,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.OwnerContribution(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    foreignExchangeDetails)),
        Map.entry(
            PostingOriginKind.OWNER_WITHDRAWAL,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                new BookkeepingEntry.OwnerWithdrawal(
                    journalEntry.effectiveDate(),
                    requiredPrimaryDebitAccountCode(postingRow),
                    requiredPrimaryCreditAccountCode(postingRow),
                    requiredEntryAmount(postingRow),
                    foreignExchangeDetails)),
        Map.entry(
            PostingOriginKind.OPENING_POSITION,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                openingPositionEntry(journalEntry)),
        Map.entry(
            PostingOriginKind.REVERSAL,
            (postingRow, journalEntry, postingLineage, appliedTax, foreignExchangeDetails) ->
                reversalEntry(journalEntry, postingLineage, foreignExchangeDetails)));
  }

  private static BookkeepingEntry.OpeningPosition openingPositionEntry(JournalEntry journalEntry) {
    return new BookkeepingEntry.OpeningPosition(
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
  }

  private static BookkeepingEntry.Reversal reversalEntry(
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
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

  private static AccountCode requiredPrimaryDebitAccountCode(SqliteNativeStatement postingRow) {
    return new AccountCode(
        SqlitePostingMapper.requiredText(
            postingRow, SqlitePostingColumnIndexes.COL_ENTRY_PRIMARY_DEBIT_ACCOUNT_CODE));
  }

  private static AccountCode requiredPrimaryCreditAccountCode(SqliteNativeStatement postingRow) {
    return new AccountCode(
        SqlitePostingMapper.requiredText(
            postingRow, SqlitePostingColumnIndexes.COL_ENTRY_PRIMARY_CREDIT_ACCOUNT_CODE));
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

  private static @Nullable SettlementAdjunct settlementAdjunct(SqliteNativeStatement postingRow) {
    String accountCode =
        postingRow.columnText(SqlitePostingColumnIndexes.COL_ENTRY_ADJUNCT_ACCOUNT_CODE);
    if (accountCode == null) {
      return null;
    }
    return new SettlementAdjunct(
        new AccountCode(accountCode),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            Money.ofMinorUnits(
                dev.erst.fingrind.core.CurrencyUnit.of(
                    SqlitePostingMapper.requiredText(
                        postingRow, SqlitePostingColumnIndexes.COL_ENTRY_AMOUNT_CURRENCY_CODE)),
                postingRow.columnLong(SqlitePostingColumnIndexes.COL_ENTRY_ADJUNCT_AMOUNT_MINOR))));
  }

  private static @Nullable InventoryRelief inventoryRelief(
      JournalEntry journalEntry,
      SqliteNativeStatement postingRow,
      @Nullable AppliedTax appliedTax) {
    AccountCode primaryDebitAccountCode = requiredPrimaryDebitAccountCode(postingRow);
    AccountCode primaryCreditAccountCode = requiredPrimaryCreditAccountCode(postingRow);
    @Nullable AccountCode taxAccountCode = appliedTax == null ? null : appliedTax.taxAccountCode();
    java.util.List<dev.erst.fingrind.core.JournalLine> reliefDebits =
        journalEntry.lines().stream()
            .filter(line -> line.side() == dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT)
            .filter(line -> !line.accountCode().equals(primaryDebitAccountCode))
            .toList();
    java.util.List<dev.erst.fingrind.core.JournalLine> reliefCredits =
        journalEntry.lines().stream()
            .filter(line -> line.side() == dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT)
            .filter(line -> !line.accountCode().equals(primaryCreditAccountCode))
            .filter(line -> taxAccountCode == null || !line.accountCode().equals(taxAccountCode))
            .toList();
    if (reliefDebits.size() != 1 || reliefCredits.size() != 1) {
      return null;
    }
    dev.erst.fingrind.core.JournalLine debitLine = reliefDebits.getFirst();
    dev.erst.fingrind.core.JournalLine creditLine = reliefCredits.getFirst();
    if (!debitLine.amount().equals(creditLine.amount())) {
      return null;
    }
    return new InventoryRelief(
        creditLine.accountCode(),
        debitLine.accountCode(),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(debitLine.amount().money()));
  }
}
