package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Rebuilds typed inventory acquisitions, capitalization, and quantity adjustments. */
final class SqliteInventoryOriginatingEntryMapper {
  private static final Map<PostingOriginKind, SqlitePostingOriginatingEntryBuilder> ENTRY_BUILDERS =
      Map.ofEntries(
          Map.entry(
              PostingOriginKind.PURCHASE_SETTLED,
              SqliteInventoryOriginatingEntryMapper::purchaseSettledEntry),
          Map.entry(
              PostingOriginKind.PURCHASE_ON_CREDIT,
              SqliteInventoryOriginatingEntryMapper::purchaseOnCreditEntry),
          Map.entry(
              PostingOriginKind.INVENTORY_CAPITALIZATION_SETTLED,
              SqliteInventoryOriginatingEntryMapper::capitalizationSettledEntry),
          Map.entry(
              PostingOriginKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
              SqliteInventoryOriginatingEntryMapper::capitalizationOnCreditEntry),
          Map.entry(
              PostingOriginKind.INVENTORY_WRITE_DOWN,
              SqliteInventoryOriginatingEntryMapper::writeDownEntry),
          Map.entry(
              PostingOriginKind.INVENTORY_SHRINKAGE,
              SqliteInventoryOriginatingEntryMapper::shrinkageEntry),
          Map.entry(
              PostingOriginKind.INVENTORY_COUNT_INCREASE,
              SqliteInventoryOriginatingEntryMapper::countIncreaseEntry));

  private SqliteInventoryOriginatingEntryMapper() {}

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

  private static BookkeepingEntry purchaseSettledEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.PurchaseSettled(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryQuantity(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryUnitCost(postingRow),
        null,
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry purchaseOnCreditEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.PurchaseOnCredit(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryQuantity(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryUnitCost(postingRow),
        null,
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry capitalizationSettledEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry capitalizationOnCreditEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow),
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry writeDownEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails ignoredForeignExchangeDetails) {
    return new InventoryBookkeepingEntryVariants.InventoryWriteDown(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow));
  }

  private static BookkeepingEntry shrinkageEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails ignoredForeignExchangeDetails) {
    return new InventoryBookkeepingEntryVariants.InventoryShrinkage(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryQuantity(postingRow),
        null);
  }

  private static BookkeepingEntry countIncreaseEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel ignoredPostingLineage,
      @Nullable AppliedTax ignoredAppliedTax,
      @Nullable ForeignExchangeDetails ignoredForeignExchangeDetails) {
    return new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryQuantity(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryUnitCost(postingRow),
        null);
  }
}
