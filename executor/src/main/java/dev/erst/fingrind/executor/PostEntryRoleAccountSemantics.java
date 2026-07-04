package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.cash;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.distinct;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.equityContribution;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.equityWithdrawal;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.expense;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.inventory;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.payable;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.receivable;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.revenue;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.settlementAdjunct;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.AccountExpectation;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Event-role account semantics for the published business-event posting surface. */
final class PostEntryRoleAccountSemantics {
  private PostEntryRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry entry,
      String selectorField,
      String selectorValue) {
    switch (entry) {
      case BookkeepingEntry.DirectJournal journal ->
          DirectJournalEntrySemantics.validate(
              violations, accounts, selectorField, selectorValue, journal.lines());
      case BookkeepingEntry.SaleSettled sale ->
          validateSaleSettled(violations, accounts, sale, selectorField, selectorValue);
      case BookkeepingEntry.SaleOnCredit sale ->
          validateSaleOnCredit(violations, accounts, sale, selectorField, selectorValue);
      case BookkeepingEntry.PurchaseSettled purchase ->
          validatePurchaseSettled(violations, accounts, purchase, selectorField, selectorValue);
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          validatePurchaseOnCredit(violations, accounts, purchase, selectorField, selectorValue);
      case BookkeepingEntry.ExpenseSettled expense ->
          validateExpenseSettled(violations, accounts, expense, selectorField, selectorValue);
      case BookkeepingEntry.ExpenseOnCredit expense ->
          validateExpenseOnCredit(violations, accounts, expense, selectorField, selectorValue);
      case BookkeepingEntry.Receipt receipt ->
          validateReceipt(violations, accounts, receipt, selectorField, selectorValue);
      case BookkeepingEntry.Payment payment ->
          validatePayment(violations, accounts, payment, selectorField, selectorValue);
      case BookkeepingEntry.OwnerContribution contribution ->
          validateOwnerContribution(
              violations, accounts, contribution, selectorField, selectorValue);
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          validateOwnerWithdrawal(violations, accounts, withdrawal, selectorField, selectorValue);
      case BookkeepingEntry.OpeningPosition _ -> {}
      case BookkeepingEntry.Reversal _ -> {}
    }
  }

  private static void validateSaleSettled(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.SaleSettled sale,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        cash(sale.cashAccountCode(), "cashAccountCode"),
        revenue(sale.revenueAccountCode(), "revenueAccountCode"));
    validateOptionalInventoryRelief(
        violations, accounts, sale.inventoryRelief(), selectorField, selectorValue);
  }

  private static void validateSaleOnCredit(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.SaleOnCredit sale,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        receivable(sale.receivableAccountCode(), "receivableAccountCode"),
        revenue(sale.revenueAccountCode(), "revenueAccountCode"));
    validateOptionalInventoryRelief(
        violations, accounts, sale.inventoryRelief(), selectorField, selectorValue);
  }

  private static void validatePurchaseSettled(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.PurchaseSettled purchase,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        inventory(purchase.inventoryAccountCode(), "inventoryAccountCode"),
        cash(purchase.cashAccountCode(), "cashAccountCode"));
  }

  private static void validatePurchaseOnCredit(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.PurchaseOnCredit purchase,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        inventory(purchase.inventoryAccountCode(), "inventoryAccountCode"),
        payable(purchase.payableAccountCode(), "payableAccountCode"));
  }

  private static void validateExpenseSettled(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.ExpenseSettled expenseEntry,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        expense(expenseEntry.expenseAccountCode(), "expenseAccountCode"),
        cash(expenseEntry.cashAccountCode(), "cashAccountCode"));
  }

  private static void validateExpenseOnCredit(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.ExpenseOnCredit expenseEntry,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        expense(expenseEntry.expenseAccountCode(), "expenseAccountCode"),
        payable(expenseEntry.payableAccountCode(), "payableAccountCode"));
  }

  private static void validateReceipt(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.Receipt receipt,
      String selectorField,
      String selectorValue) {
    validatePairWithOptionalAdjunct(
        violations,
        accounts,
        selectorField,
        selectorValue,
        cash(receipt.cashAccountCode(), "cashAccountCode"),
        receivable(receipt.receivableAccountCode(), "receivableAccountCode"),
        receipt.settlementAdjunct());
  }

  private static void validatePayment(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.Payment payment,
      String selectorField,
      String selectorValue) {
    validatePairWithOptionalAdjunct(
        violations,
        accounts,
        selectorField,
        selectorValue,
        payable(payment.payableAccountCode(), "payableAccountCode"),
        cash(payment.cashAccountCode(), "cashAccountCode"),
        payment.settlementAdjunct());
  }

  private static void validateOwnerContribution(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.OwnerContribution contribution,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        cash(contribution.cashAccountCode(), "cashAccountCode"),
        equityContribution(contribution.equityAccountCode(), "equityAccountCode"));
  }

  private static void validateOwnerWithdrawal(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.OwnerWithdrawal withdrawal,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        equityWithdrawal(withdrawal.equityAccountCode(), "equityAccountCode"),
        cash(withdrawal.cashAccountCode(), "cashAccountCode"));
  }

  private static void validatePair(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      AccountExpectation firstExpectation,
      AccountExpectation secondExpectation) {
    PostEntryRoleAccountValidationSupport.validate(
        violations,
        accounts,
        selectorField,
        selectorValue,
        distinct(firstExpectation, secondExpectation),
        firstExpectation,
        secondExpectation);
  }

  private static void validatePairWithOptionalAdjunct(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      AccountExpectation firstExpectation,
      AccountExpectation secondExpectation,
      @Nullable SettlementAdjunct settlementAdjunct) {
    validatePair(
        violations, accounts, selectorField, selectorValue, firstExpectation, secondExpectation);
    PostEntryRoleAccountValidationSupport.validateOptional(
        violations,
        accounts,
        selectorField,
        selectorValue,
        settlementAdjunct == null
            ? null
            : settlementAdjunct(settlementAdjunct.accountCode(), "settlementAdjunct.accountCode"));
  }

  private static void validateOptionalInventoryRelief(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      @Nullable InventoryRelief inventoryRelief,
      String selectorField,
      String selectorValue) {
    if (inventoryRelief == null) {
      return;
    }
    PostEntryRoleAccountValidationSupport.validate(
        violations,
        accounts,
        selectorField,
        selectorValue,
        distinct(
            inventoryRelief.inventoryAccountCode(),
            "inventoryRelief.inventoryAccountCode",
            inventoryRelief.costOfSalesAccountCode(),
            "inventoryRelief.costOfSalesAccountCode"),
        inventory(inventoryRelief.inventoryAccountCode(), "inventoryRelief.inventoryAccountCode"),
        expense(
            inventoryRelief.costOfSalesAccountCode(), "inventoryRelief.costOfSalesAccountCode"));
  }
}
