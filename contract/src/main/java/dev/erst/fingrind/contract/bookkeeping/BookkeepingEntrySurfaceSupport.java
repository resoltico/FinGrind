package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.tax.AppliedTax;
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
      case BookkeepingEntry.SaleSettled _ -> BookkeepingEntryKind.SALE_SETTLED;
      case BookkeepingEntry.SaleOnCredit _ -> BookkeepingEntryKind.SALE_ON_CREDIT;
      case BookkeepingEntry.PurchaseSettled _ -> BookkeepingEntryKind.PURCHASE_SETTLED;
      case BookkeepingEntry.PurchaseOnCredit _ -> BookkeepingEntryKind.PURCHASE_ON_CREDIT;
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          InventoryBookkeepingEntrySurfaceSupport.entryKind(inventoryEntry);
      case BookkeepingEntry.ExpenseSettled _ -> BookkeepingEntryKind.EXPENSE_SETTLED;
      case BookkeepingEntry.ExpenseOnCredit _ -> BookkeepingEntryKind.EXPENSE_ON_CREDIT;
      case BookkeepingEntry.Receipt _ -> BookkeepingEntryKind.RECEIPT;
      case BookkeepingEntry.Payment _ -> BookkeepingEntryKind.PAYMENT;
      case BookkeepingEntry.OwnerContribution _ -> BookkeepingEntryKind.OWNER_CONTRIBUTION;
      case BookkeepingEntry.OwnerWithdrawal _ -> BookkeepingEntryKind.OWNER_WITHDRAWAL;
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
      case BookkeepingEntry.SaleSettled _ -> PostingOriginKind.SALE_SETTLED;
      case BookkeepingEntry.SaleOnCredit _ -> PostingOriginKind.SALE_ON_CREDIT;
      case BookkeepingEntry.PurchaseSettled _ -> PostingOriginKind.PURCHASE_SETTLED;
      case BookkeepingEntry.PurchaseOnCredit _ -> PostingOriginKind.PURCHASE_ON_CREDIT;
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          InventoryBookkeepingEntrySurfaceSupport.postingOriginKind(inventoryEntry);
      case BookkeepingEntry.ExpenseSettled _ -> PostingOriginKind.EXPENSE_SETTLED;
      case BookkeepingEntry.ExpenseOnCredit _ -> PostingOriginKind.EXPENSE_ON_CREDIT;
      case BookkeepingEntry.Receipt _ -> PostingOriginKind.RECEIPT;
      case BookkeepingEntry.Payment _ -> PostingOriginKind.PAYMENT;
      case BookkeepingEntry.OwnerContribution _ -> PostingOriginKind.OWNER_CONTRIBUTION;
      case BookkeepingEntry.OwnerWithdrawal _ -> PostingOriginKind.OWNER_WITHDRAWAL;
      case BookkeepingEntry.OpeningPosition _ -> PostingOriginKind.OPENING_POSITION;
      case BookkeepingEntry.Reversal _ -> PostingOriginKind.REVERSAL;
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
      case BookkeepingEntry.SaleSettled saleSettled -> saleJournalEntry(saleSettled);
      case BookkeepingEntry.SaleOnCredit saleOnCredit -> saleJournalEntry(saleOnCredit);
      case BookkeepingEntry.PurchaseSettled purchaseSettled ->
          purchaseJournalEntry(purchaseSettled);
      case BookkeepingEntry.PurchaseOnCredit purchaseOnCredit ->
          purchaseJournalEntry(purchaseOnCredit);
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          InventoryBookkeepingEntrySurfaceSupport.journalEntry(inventoryEntry);
      case BookkeepingEntry.ExpenseSettled expenseSettled -> expenseJournalEntry(expenseSettled);
      case BookkeepingEntry.ExpenseOnCredit expenseOnCredit -> expenseJournalEntry(expenseOnCredit);
      case BookkeepingEntry.Receipt receipt ->
          BookkeepingEntrySupport.receiptEntry(
              receipt.effectiveDate(),
              receipt.cashAccountCode(),
              receipt.receivableAccountCode(),
              receipt.amount(),
              receipt.settlementAdjunct());
      case BookkeepingEntry.Payment payment ->
          BookkeepingEntrySupport.paymentEntry(
              payment.effectiveDate(),
              payment.payableAccountCode(),
              payment.cashAccountCode(),
              payment.amount(),
              payment.settlementAdjunct());
      case BookkeepingEntry.OwnerContribution ownerContribution ->
          BookkeepingEntrySupport.pairedEntry(
              ownerContribution.effectiveDate(),
              ownerContribution.cashAccountCode(),
              ownerContribution.equityAccountCode(),
              ownerContribution.amount());
      case BookkeepingEntry.OwnerWithdrawal ownerWithdrawal ->
          BookkeepingEntrySupport.pairedEntry(
              ownerWithdrawal.effectiveDate(),
              ownerWithdrawal.equityAccountCode(),
              ownerWithdrawal.cashAccountCode(),
              ownerWithdrawal.amount());
      case BookkeepingEntry.OpeningPosition openingPosition ->
          new JournalEntry(openingPosition.effectiveDate(), openingPositionLines(openingPosition));
      case BookkeepingEntry.Reversal reversal -> reversal.journalEntry();
    };
  }

  private static JournalEntry saleJournalEntry(BookkeepingEntry.SaleSettled entry) {
    return BookkeepingEntrySupport.saleEntry(
        entry.effectiveDate(),
        entry.cashAccountCode(),
        entry.revenueAccountCode(),
        entry.amount(),
        entry.inventoryRelief(),
        entry.resolvedInventoryCosting(),
        entry.taxSelection() == null
            ? null
            : BookkeepingEntryTaxValidationSupport.requireResolvedAppliedTax(
                entry.appliedTax(), "saleSettled"));
  }

  private static JournalEntry purchaseJournalEntry(BookkeepingEntry.PurchaseSettled entry) {
    if (entry.taxSelection() == null) {
      return BookkeepingEntrySupport.pairedEntry(
          entry.effectiveDate(),
          entry.inventoryAccountCode(),
          entry.cashAccountCode(),
          BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryAcquisition(
                  entry.resolvedInventoryAcquisition(), "purchaseSettled")
              .carryingCost());
    }
    return BookkeepingEntrySupport.inventoryCostEntry(
        entry.effectiveDate(),
        entry.inventoryAccountCode(),
        entry.cashAccountCode(),
        BookkeepingEntryTaxValidationSupport.requireResolvedAppliedTax(
            entry.appliedTax(), "purchaseSettled"));
  }

  private static JournalEntry purchaseJournalEntry(BookkeepingEntry.PurchaseOnCredit entry) {
    if (entry.taxSelection() == null) {
      return BookkeepingEntrySupport.pairedEntry(
          entry.effectiveDate(),
          entry.inventoryAccountCode(),
          entry.payableAccountCode(),
          BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryAcquisition(
                  entry.resolvedInventoryAcquisition(), "purchaseOnCredit")
              .carryingCost());
    }
    return BookkeepingEntrySupport.inventoryCostEntry(
        entry.effectiveDate(),
        entry.inventoryAccountCode(),
        entry.payableAccountCode(),
        BookkeepingEntryTaxValidationSupport.requireResolvedAppliedTax(
            entry.appliedTax(), "purchaseOnCredit"));
  }

  private static JournalEntry saleJournalEntry(BookkeepingEntry.SaleOnCredit entry) {
    return BookkeepingEntrySupport.saleEntry(
        entry.effectiveDate(),
        entry.receivableAccountCode(),
        entry.revenueAccountCode(),
        entry.amount(),
        entry.inventoryRelief(),
        entry.resolvedInventoryCosting(),
        entry.taxSelection() == null
            ? null
            : BookkeepingEntryTaxValidationSupport.requireResolvedAppliedTax(
                entry.appliedTax(), "saleOnCredit"));
  }

  private static JournalEntry expenseJournalEntry(BookkeepingEntry.ExpenseSettled entry) {
    if (entry.taxSelection() == null) {
      return BookkeepingEntrySupport.pairedEntry(
          entry.effectiveDate(),
          entry.expenseAccountCode(),
          entry.cashAccountCode(),
          entry.amount());
    }
    AppliedTax resolvedTax =
        BookkeepingEntryTaxValidationSupport.requireResolvedAppliedTax(
            entry.appliedTax(), "expenseSettled");
    return BookkeepingEntrySupport.expenseEntry(
        entry.effectiveDate(), entry.expenseAccountCode(), entry.cashAccountCode(), resolvedTax);
  }

  private static JournalEntry expenseJournalEntry(BookkeepingEntry.ExpenseOnCredit entry) {
    if (entry.taxSelection() == null) {
      return BookkeepingEntrySupport.pairedEntry(
          entry.effectiveDate(),
          entry.expenseAccountCode(),
          entry.payableAccountCode(),
          entry.amount());
    }
    AppliedTax resolvedTax =
        BookkeepingEntryTaxValidationSupport.requireResolvedAppliedTax(
            entry.appliedTax(), "expenseOnCredit");
    return BookkeepingEntrySupport.expenseEntry(
        entry.effectiveDate(), entry.expenseAccountCode(), entry.payableAccountCode(), resolvedTax);
  }

  static List<JournalLine> openingPositionLines(BookkeepingEntry.OpeningPosition entry) {
    return entry.balances().stream()
        .map(
            balance ->
                new JournalLine(balance.accountCode(), balance.side(), balance.amount().toMoney()))
        .toList();
  }
}
