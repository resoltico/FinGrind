package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;

/** Writes caller-authored typed-entry fingerprint fields other than shared FX details. */
final class RequestFingerprintTypedEntryWriter {
  private RequestFingerprintTypedEntryWriter() {}

  static void append(StringBuilder canonical, BookkeepingEntry entry) {
    switch (entry) {
      case BookkeepingEntry.DirectJournal _ -> {}
      case BookkeepingEntry.SaleSettled sale -> appendSaleSettled(canonical, sale);
      case BookkeepingEntry.SaleOnCredit sale -> appendSaleOnCredit(canonical, sale);
      case BookkeepingEntry.PurchaseSettled purchase -> appendPurchaseSettled(canonical, purchase);
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          appendPurchaseOnCredit(canonical, purchase);
      case BookkeepingEntry.ExpenseSettled expense -> appendExpenseSettled(canonical, expense);
      case BookkeepingEntry.ExpenseOnCredit expense -> appendExpenseOnCredit(canonical, expense);
      case BookkeepingEntry.Receipt receipt -> appendReceipt(canonical, receipt);
      case BookkeepingEntry.Payment payment -> appendPayment(canonical, payment);
      case BookkeepingEntry.OwnerContribution contribution ->
          appendOwnerContribution(canonical, contribution);
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          appendOwnerWithdrawal(canonical, withdrawal);
      case BookkeepingEntry.OpeningPosition _ -> {}
      case BookkeepingEntry.Reversal _ -> {}
    }
  }

  private static void appendSaleSettled(
      StringBuilder canonical, BookkeepingEntry.SaleSettled sale) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "cashAccountCode", sale.cashAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "revenueAccountCode", sale.revenueAccountCode());
    RequestFingerprintEntryFieldWriter.appendTaxedAmount(
        canonical, sale.amount(), sale.taxSelection(), sale.appliedTax());
    RequestFingerprintEntryFieldWriter.appendOptionalInventoryRelief(
        canonical, sale.inventoryRelief());
  }

  private static void appendSaleOnCredit(
      StringBuilder canonical, BookkeepingEntry.SaleOnCredit sale) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "receivableAccountCode", sale.receivableAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "revenueAccountCode", sale.revenueAccountCode());
    RequestFingerprintEntryFieldWriter.appendTaxedAmount(
        canonical, sale.amount(), sale.taxSelection(), sale.appliedTax());
    RequestFingerprintEntryFieldWriter.appendOptionalInventoryRelief(
        canonical, sale.inventoryRelief());
  }

  private static void appendExpenseSettled(
      StringBuilder canonical, BookkeepingEntry.ExpenseSettled expense) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "expenseAccountCode", expense.expenseAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "cashAccountCode", expense.cashAccountCode());
    RequestFingerprintEntryFieldWriter.appendTaxedAmount(
        canonical, expense.amount(), expense.taxSelection(), expense.appliedTax());
  }

  private static void appendPurchaseSettled(
      StringBuilder canonical, BookkeepingEntry.PurchaseSettled purchase) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "inventoryAccountCode", purchase.inventoryAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "cashAccountCode", purchase.cashAccountCode());
    RequestFingerprintEntryFieldWriter.appendAmount(canonical, purchase.amount());
  }

  private static void appendPurchaseOnCredit(
      StringBuilder canonical, BookkeepingEntry.PurchaseOnCredit purchase) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "inventoryAccountCode", purchase.inventoryAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "payableAccountCode", purchase.payableAccountCode());
    RequestFingerprintEntryFieldWriter.appendAmount(canonical, purchase.amount());
  }

  private static void appendExpenseOnCredit(
      StringBuilder canonical, BookkeepingEntry.ExpenseOnCredit expense) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "expenseAccountCode", expense.expenseAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "payableAccountCode", expense.payableAccountCode());
    RequestFingerprintEntryFieldWriter.appendTaxedAmount(
        canonical, expense.amount(), expense.taxSelection(), expense.appliedTax());
  }

  private static void appendReceipt(StringBuilder canonical, BookkeepingEntry.Receipt receipt) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "cashAccountCode", receipt.cashAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "receivableAccountCode", receipt.receivableAccountCode());
    RequestFingerprintEntryFieldWriter.appendAmount(canonical, receipt.amount());
    RequestFingerprintEntryFieldWriter.appendOptionalSettlementAdjunct(
        canonical, receipt.settlementAdjunct());
  }

  private static void appendPayment(StringBuilder canonical, BookkeepingEntry.Payment payment) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "payableAccountCode", payment.payableAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "cashAccountCode", payment.cashAccountCode());
    RequestFingerprintEntryFieldWriter.appendAmount(canonical, payment.amount());
    RequestFingerprintEntryFieldWriter.appendOptionalSettlementAdjunct(
        canonical, payment.settlementAdjunct());
  }

  private static void appendOwnerContribution(
      StringBuilder canonical, BookkeepingEntry.OwnerContribution contribution) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "cashAccountCode", contribution.cashAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "equityAccountCode", contribution.equityAccountCode());
    RequestFingerprintEntryFieldWriter.appendAmount(canonical, contribution.amount());
  }

  private static void appendOwnerWithdrawal(
      StringBuilder canonical, BookkeepingEntry.OwnerWithdrawal withdrawal) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "equityAccountCode", withdrawal.equityAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "cashAccountCode", withdrawal.cashAccountCode());
    RequestFingerprintEntryFieldWriter.appendAmount(canonical, withdrawal.amount());
  }
}
