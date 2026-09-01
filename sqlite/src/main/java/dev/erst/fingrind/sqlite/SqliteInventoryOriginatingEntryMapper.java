package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingOriginKind;
import org.jspecify.annotations.Nullable;

/** Rebuilds typed inventory acquisitions, capitalization, and quantity adjustments. */
final class SqliteInventoryOriginatingEntryMapper {
  private SqliteInventoryOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      PostingOriginKind postingOriginKind,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      @Nullable AppliedTax appliedTax,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return switch (postingOriginKind) {
      case PURCHASE_SETTLED ->
          purchaseSettledEntry(
              postingRow,
              journalEntry,
              appliedTax,
              resolvedInventoryAcquisition,
              foreignExchangeDetails);
      case PURCHASE_ON_CREDIT ->
          purchaseOnCreditEntry(
              postingRow,
              journalEntry,
              appliedTax,
              resolvedInventoryAcquisition,
              foreignExchangeDetails);
      case INVENTORY_CAPITALIZATION_SETTLED ->
          capitalizationSettledEntry(postingRow, journalEntry, appliedTax, foreignExchangeDetails);
      case INVENTORY_CAPITALIZATION_ON_CREDIT ->
          capitalizationOnCreditEntry(postingRow, journalEntry, appliedTax, foreignExchangeDetails);
      case INVENTORY_WRITE_DOWN -> writeDownEntry(postingRow, journalEntry);
      case INVENTORY_SHRINKAGE -> shrinkageEntry(postingRow, journalEntry);
      case INVENTORY_COUNT_INCREASE -> countIncreaseEntry(postingRow, journalEntry);
      default -> null;
    };
  }

  private static BookkeepingEntry purchaseSettledEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      @Nullable AppliedTax appliedTax,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.PurchaseSettled(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryQuantity(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryUnitCost(postingRow),
        resolvedInventoryAcquisition,
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry purchaseOnCreditEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      @Nullable AppliedTax appliedTax,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return new BookkeepingEntry.PurchaseOnCredit(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryQuantity(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryUnitCost(postingRow),
        resolvedInventoryAcquisition,
        foreignExchangeDetails,
        SqlitePostingOriginatingEntryMappingSupport.taxSelection(appliedTax),
        appliedTax);
  }

  private static BookkeepingEntry capitalizationSettledEntry(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
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
      SqliteNativeStatement postingRow, JournalEntry journalEntry) {
    return new InventoryBookkeepingEntryVariants.InventoryWriteDown(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow));
  }

  private static BookkeepingEntry shrinkageEntry(
      SqliteNativeStatement postingRow, JournalEntry journalEntry) {
    return new InventoryBookkeepingEntryVariants.InventoryShrinkage(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryQuantity(postingRow),
        null);
  }

  private static BookkeepingEntry countIncreaseEntry(
      SqliteNativeStatement postingRow, JournalEntry journalEntry) {
    return new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
        journalEntry.effectiveDate(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryQuantity(postingRow),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryUnitCost(postingRow),
        null);
  }
}
