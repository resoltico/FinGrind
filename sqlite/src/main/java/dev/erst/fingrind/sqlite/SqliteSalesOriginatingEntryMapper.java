package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Rebuilds sales and their executor-derived inventory relief from persisted journal facts. */
final class SqliteSalesOriginatingEntryMapper {
  private static final Map<PostingOriginKind, SqlitePostingOriginatingEntryBuilder> ENTRY_BUILDERS =
      Map.of(
          PostingOriginKind.SALE_SETTLED, SqliteSalesOriginatingEntryMapper::saleSettledEntry,
          PostingOriginKind.SALE_ON_CREDIT, SqliteSalesOriginatingEntryMapper::saleOnCreditEntry);

  private SqliteSalesOriginatingEntryMapper() {}

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

  private static BookkeepingEntry saleSettledEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.SaleSettled(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        inventoryRelief(journalEntry, postingRow, appliedTax),
        null,
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry saleOnCreditEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.SaleOnCredit(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        inventoryRelief(journalEntry, postingRow, appliedTax),
        null,
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static @Nullable InventoryRelief inventoryRelief(
      JournalEntry journalEntry,
      SqliteNativeStatement postingRow,
      @Nullable AppliedTax appliedTax) {
    String persistedQuantity = postingRow.columnText(SqlitePostingColumnIndexes.COL_ENTRY_QUANTITY);
    if (persistedQuantity == null) {
      return null;
    }
    AccountCode primaryDebitAccountCode =
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow);
    AccountCode primaryCreditAccountCode =
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow);
    @Nullable AccountCode taxAccountCode = appliedTax == null ? null : appliedTax.taxAccountCode();
    List<JournalLine> reliefDebits =
        journalEntry.lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.DEBIT)
            .filter(line -> !line.accountCode().equals(primaryDebitAccountCode))
            .toList();
    List<JournalLine> reliefCredits =
        journalEntry.lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.CREDIT)
            .filter(line -> !line.accountCode().equals(primaryCreditAccountCode))
            .filter(line -> taxAccountCode == null || !line.accountCode().equals(taxAccountCode))
            .toList();
    if (reliefDebits.size() != 1 || reliefCredits.size() != 1) {
      throw new IllegalStateException(
          "Persisted sale originating entry with inventory quantity must resolve exactly one inventory relief debit and credit line.");
    }
    JournalLine debitLine = reliefDebits.getFirst();
    JournalLine creditLine = reliefCredits.getFirst();
    if (!debitLine.amount().equals(creditLine.amount())) {
      throw new IllegalStateException(
          "Persisted sale originating entry with inventory quantity must carry matching relief journal amounts.");
    }
    return new InventoryRelief(
        creditLine.accountCode(), debitLine.accountCode(), new QuantityText(persistedQuantity));
  }
}
