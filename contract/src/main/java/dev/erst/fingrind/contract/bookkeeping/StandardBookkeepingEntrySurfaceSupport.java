package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingOriginKind;

/** Maps standard typed business events to their posting vocabulary and caller-derived journal. */
final class StandardBookkeepingEntrySurfaceSupport {
  private StandardBookkeepingEntrySurfaceSupport() {}

  static BookkeepingEntryKind entryKind(StandardBookkeepingEntryVariants entry) {
    return switch (entry) {
      case BookkeepingEntry.SaleSettled _ -> BookkeepingEntryKind.SALE_SETTLED;
      case BookkeepingEntry.SaleOnCredit _ -> BookkeepingEntryKind.SALE_ON_CREDIT;
      case BookkeepingEntry.PurchaseSettled _ -> BookkeepingEntryKind.PURCHASE_SETTLED;
      case BookkeepingEntry.PurchaseOnCredit _ -> BookkeepingEntryKind.PURCHASE_ON_CREDIT;
      case BookkeepingEntry.ExpenseSettled _ -> BookkeepingEntryKind.EXPENSE_SETTLED;
      case BookkeepingEntry.ExpenseOnCredit _ -> BookkeepingEntryKind.EXPENSE_ON_CREDIT;
      case BookkeepingEntry.Receipt _ -> BookkeepingEntryKind.RECEIPT;
      case BookkeepingEntry.Payment _ -> BookkeepingEntryKind.PAYMENT;
      case BookkeepingEntry.OwnerContribution _ -> BookkeepingEntryKind.OWNER_CONTRIBUTION;
      case BookkeepingEntry.OwnerWithdrawal _ -> BookkeepingEntryKind.OWNER_WITHDRAWAL;
    };
  }

  static PostingOriginKind postingOriginKind(StandardBookkeepingEntryVariants entry) {
    return switch (entry) {
      case BookkeepingEntry.SaleSettled _ -> PostingOriginKind.SALE_SETTLED;
      case BookkeepingEntry.SaleOnCredit _ -> PostingOriginKind.SALE_ON_CREDIT;
      case BookkeepingEntry.PurchaseSettled _ -> PostingOriginKind.PURCHASE_SETTLED;
      case BookkeepingEntry.PurchaseOnCredit _ -> PostingOriginKind.PURCHASE_ON_CREDIT;
      case BookkeepingEntry.ExpenseSettled _ -> PostingOriginKind.EXPENSE_SETTLED;
      case BookkeepingEntry.ExpenseOnCredit _ -> PostingOriginKind.EXPENSE_ON_CREDIT;
      case BookkeepingEntry.Receipt _ -> PostingOriginKind.RECEIPT;
      case BookkeepingEntry.Payment _ -> PostingOriginKind.PAYMENT;
      case BookkeepingEntry.OwnerContribution _ -> PostingOriginKind.OWNER_CONTRIBUTION;
      case BookkeepingEntry.OwnerWithdrawal _ -> PostingOriginKind.OWNER_WITHDRAWAL;
    };
  }

  static JournalEntry journalEntry(StandardBookkeepingEntryVariants entry) {
    return switch (entry) {
      case BookkeepingEntry.SaleSettled saleSettled -> saleJournalEntry(saleSettled);
      case BookkeepingEntry.SaleOnCredit saleOnCredit -> saleJournalEntry(saleOnCredit);
      case BookkeepingEntry.PurchaseSettled purchaseSettled ->
          purchaseJournalEntry(purchaseSettled);
      case BookkeepingEntry.PurchaseOnCredit purchaseOnCredit ->
          purchaseJournalEntry(purchaseOnCredit);
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
}
