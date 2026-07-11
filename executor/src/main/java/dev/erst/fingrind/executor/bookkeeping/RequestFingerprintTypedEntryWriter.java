package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;

/** Writes caller-authored typed-entry fingerprint fields other than shared FX details. */
final class RequestFingerprintTypedEntryWriter {
  private RequestFingerprintTypedEntryWriter() {}

  static void append(StringBuilder canonical, BookkeepingEntry entry) {
    switch (entry) {
      case BookkeepingEntry.DirectJournal _ -> {}
      case BookkeepingEntry.SaleSettled sale -> appendSaleSettled(canonical, sale);
      case BookkeepingEntry.SaleOnCredit sale -> appendSaleOnCredit(canonical, sale);
      case BookkeepingEntry.PurchaseSettled purchase ->
          RequestFingerprintInventoryEntryWriter.append(canonical, purchase);
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          RequestFingerprintInventoryEntryWriter.append(canonical, purchase);
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          RequestFingerprintInventoryEntryWriter.append(canonical, inventoryEntry);
      case BookkeepingEntry.ExpenseSettled expense -> appendExpenseSettled(canonical, expense);
      case BookkeepingEntry.ExpenseOnCredit expense -> appendExpenseOnCredit(canonical, expense);
      case BookkeepingEntry.Receipt receipt -> appendReceipt(canonical, receipt);
      case BookkeepingEntry.Payment payment -> appendPayment(canonical, payment);
      case BookkeepingEntry.OwnerContribution contribution ->
          appendOwnerContribution(canonical, contribution);
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          appendOwnerWithdrawal(canonical, withdrawal);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          appendOpeningPosition(canonical, openingPosition);
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

  private static void appendOpeningPosition(
      StringBuilder canonical, BookkeepingEntry.OpeningPosition openingPosition) {
    for (int index = 0; index < openingPosition.balances().size(); index++) {
      BookkeepingEntry.OpeningPosition.OpeningAccountBalance balance =
          openingPosition.balances().get(index);
      String prefix = "callerAuthoredEntry.openingBalances[" + index + "].";
      RequestFingerprintEntryFieldWriter.appendField(
          canonical, prefix + "accountCode", balance.accountCode().value());
      RequestFingerprintEntryFieldWriter.appendField(
          canonical, prefix + "side", balance.side().wireValue());
      RequestFingerprintEntryFieldWriter.appendField(
          canonical, prefix + "amountCurrency", balance.amount().currencyCode());
      RequestFingerprintEntryFieldWriter.appendField(
          canonical, prefix + "amountMinorUnits", balance.amount().minorUnits());
      RequestFingerprintEntryFieldWriter.appendField(
          canonical,
          prefix + "quantity",
          balance.quantity() == null ? "" : balance.quantity().value());
    }
  }
}
