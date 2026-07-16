package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.List;

/** Package-private policy and journal support for the public bookkeeping-entry surface. */
final class BookkeepingEntrySurfaceSupport {
  private BookkeepingEntrySurfaceSupport() {}

  static BookkeepingEntryKind entryKind(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.DirectJournal _ -> BookkeepingEntryKind.DIRECT_JOURNAL;
      case TypedBookkeepingEntry typedEntry -> typedEntryKind(typedEntry);
      case BookkeepingEntry.OpeningPosition _ -> BookkeepingEntryKind.OPENING_POSITION;
      case BookkeepingEntry.Reversal _ -> BookkeepingEntryKind.REVERSAL;
    };
  }

  static PostingKind postingKind(BookkeepingEntry entry) {
    if (entry instanceof BookkeepingEntry.OpeningPosition) {
      return PostingKind.OPENING_BALANCE;
    }
    return PostingKind.STANDARD;
  }

  static PostingOriginKind postingOriginKind(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.DirectJournal _ -> PostingOriginKind.DIRECT_JOURNAL;
      case TypedBookkeepingEntry typedEntry -> typedPostingOriginKind(typedEntry);
      case BookkeepingEntry.OpeningPosition _ -> PostingOriginKind.OPENING_POSITION;
      case BookkeepingEntry.Reversal _ -> PostingOriginKind.REVERSAL;
    };
  }

  private static BookkeepingEntryKind typedEntryKind(TypedBookkeepingEntry entry) {
    return switch (entry) {
      case StandardBookkeepingEntryVariants standardEntry ->
          StandardBookkeepingEntrySurfaceSupport.entryKind(standardEntry);
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          InventoryBookkeepingEntrySurfaceSupport.entryKind(inventoryEntry);
      case AccrualCutoffBookkeepingEntryVariants accrualCutoffEntry ->
          AccrualCutoffBookkeepingEntrySurfaceSupport.entryKind(accrualCutoffEntry);
      case LatvianPayrollBookkeepingEntryVariants payrollEntry ->
          LatvianPayrollBookkeepingEntrySurfaceSupport.entryKind(payrollEntry);
      case FixedAssetBookkeepingEntryVariants fixedAssetEntry ->
          FixedAssetBookkeepingEntrySurfaceSupport.entryKind(fixedAssetEntry);
      case FinancingBookkeepingEntryVariants financingEntry ->
          FinancingBookkeepingEntrySurfaceSupport.entryKind(financingEntry);
      case RealizedForeignExchangeBookkeepingEntryVariants realizedForeignExchangeEntry ->
          RealizedForeignExchangeBookkeepingEntrySurfaceSupport.entryKind(
              realizedForeignExchangeEntry);
    };
  }

  private static PostingOriginKind typedPostingOriginKind(TypedBookkeepingEntry entry) {
    return switch (entry) {
      case StandardBookkeepingEntryVariants standardEntry ->
          StandardBookkeepingEntrySurfaceSupport.postingOriginKind(standardEntry);
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          InventoryBookkeepingEntrySurfaceSupport.postingOriginKind(inventoryEntry);
      case AccrualCutoffBookkeepingEntryVariants accrualCutoffEntry ->
          AccrualCutoffBookkeepingEntrySurfaceSupport.postingOriginKind(accrualCutoffEntry);
      case LatvianPayrollBookkeepingEntryVariants payrollEntry ->
          LatvianPayrollBookkeepingEntrySurfaceSupport.postingOriginKind(payrollEntry);
      case FixedAssetBookkeepingEntryVariants fixedAssetEntry ->
          FixedAssetBookkeepingEntrySurfaceSupport.postingOriginKind(fixedAssetEntry);
      case FinancingBookkeepingEntryVariants financingEntry ->
          FinancingBookkeepingEntrySurfaceSupport.postingOriginKind(financingEntry);
      case RealizedForeignExchangeBookkeepingEntryVariants realizedForeignExchangeEntry ->
          RealizedForeignExchangeBookkeepingEntrySurfaceSupport.postingOriginKind(
              realizedForeignExchangeEntry);
    };
  }

  static PostingLineage postingLineage(BookkeepingEntry entry) {
    if (entry instanceof BookkeepingEntry.Reversal reversal) {
      return reversal.reversal();
    }
    return PostingLineage.direct();
  }

  static JournalEntry journalEntry(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.DirectJournal directJournal -> directJournal.journalEntry();
      case TypedBookkeepingEntry typedEntry -> typedJournalEntry(typedEntry);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          new JournalEntry(openingPosition.effectiveDate(), openingPositionLines(openingPosition));
      case BookkeepingEntry.Reversal reversal -> reversal.journalEntry();
    };
  }

  private static JournalEntry typedJournalEntry(TypedBookkeepingEntry entry) {
    return switch (entry) {
      case StandardBookkeepingEntryVariants standardEntry ->
          StandardBookkeepingEntrySurfaceSupport.journalEntry(standardEntry);
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          InventoryBookkeepingEntrySurfaceSupport.journalEntry(inventoryEntry);
      case AccrualCutoffBookkeepingEntryVariants accrualCutoffEntry ->
          AccrualCutoffBookkeepingEntrySurfaceSupport.journalEntry(accrualCutoffEntry);
      case LatvianPayrollBookkeepingEntryVariants payrollEntry ->
          LatvianPayrollBookkeepingEntrySurfaceSupport.journalEntry(payrollEntry);
      case FixedAssetBookkeepingEntryVariants fixedAssetEntry ->
          FixedAssetBookkeepingEntrySurfaceSupport.journalEntry(fixedAssetEntry);
      case FinancingBookkeepingEntryVariants financingEntry ->
          FinancingBookkeepingEntrySurfaceSupport.journalEntry(financingEntry);
      case RealizedForeignExchangeBookkeepingEntryVariants realizedForeignExchangeEntry ->
          RealizedForeignExchangeBookkeepingEntrySurfaceSupport.journalEntry(
              realizedForeignExchangeEntry);
    };
  }

  static List<JournalLine> openingPositionLines(BookkeepingEntry.OpeningPosition entry) {
    return entry.balances().stream()
        .map(
            balance ->
                new JournalLine(balance.accountCode(), balance.side(), balance.amount().toMoney()))
        .toList();
  }
}
