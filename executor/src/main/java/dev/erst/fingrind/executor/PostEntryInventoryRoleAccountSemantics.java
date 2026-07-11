package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.expense;
import static dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.inventory;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.AccountExpectation;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Inventory-specific account-role semantics for typed inventory business events. */
final class PostEntryInventoryRoleAccountSemantics {
  private PostEntryInventoryRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      InventoryBookkeepingEntryVariants entry,
      String selectorField,
      String selectorValue) {
    switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          validate(violations, accounts, capitalization, selectorField, selectorValue);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          validate(violations, accounts, capitalization, selectorField, selectorValue);
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown ->
          validate(violations, accounts, writeDown, selectorField, selectorValue);
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          validate(violations, accounts, shrinkage, selectorField, selectorValue);
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          validate(violations, accounts, countIncrease, selectorField, selectorValue);
    }
  }

  static void validate(
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
        PostEntryRoleAccountExpectations.cash(purchase.cashAccountCode(), "cashAccountCode"));
  }

  static void validate(
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
        PostEntryRoleAccountExpectations.payable(
            purchase.payableAccountCode(), "payableAccountCode"));
  }

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        inventory(capitalization.inventoryAccountCode(), "inventoryAccountCode"),
        PostEntryRoleAccountExpectations.cash(capitalization.cashAccountCode(), "cashAccountCode"));
  }

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        inventory(capitalization.inventoryAccountCode(), "inventoryAccountCode"),
        PostEntryRoleAccountExpectations.payable(
            capitalization.payableAccountCode(), "payableAccountCode"));
  }

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown,
      String selectorField,
      String selectorValue) {
    validateInventoryExpense(
        violations,
        accounts,
        writeDown.inventoryAccountCode(),
        "inventoryAccountCode",
        writeDown.writeDownLossAccountCode(),
        "writeDownLossAccountCode",
        selectorField,
        selectorValue);
  }

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage,
      String selectorField,
      String selectorValue) {
    validateInventoryExpense(
        violations,
        accounts,
        shrinkage.inventoryAccountCode(),
        "inventoryAccountCode",
        shrinkage.shrinkageLossAccountCode(),
        "shrinkageLossAccountCode",
        selectorField,
        selectorValue);
  }

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        inventory(countIncrease.inventoryAccountCode(), "inventoryAccountCode"),
        PostEntryRoleAccountExpectations.revenue(
            countIncrease.countGainAccountCode(), "countGainAccountCode"));
  }

  static void validateOptionalInventoryRelief(
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
        PostEntryRoleAccountExpectations.distinct(
            inventoryRelief.inventoryAccountCode(),
            "inventoryRelief.inventoryAccountCode",
            inventoryRelief.costOfSalesAccountCode(),
            "inventoryRelief.costOfSalesAccountCode"),
        inventory(inventoryRelief.inventoryAccountCode(), "inventoryRelief.inventoryAccountCode"),
        expense(
            inventoryRelief.costOfSalesAccountCode(), "inventoryRelief.costOfSalesAccountCode"));
  }

  private static void validateInventoryExpense(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode inventoryAccountCode,
      String inventoryField,
      AccountCode expenseAccountCode,
      String expenseField,
      String selectorField,
      String selectorValue) {
    validatePair(
        violations,
        accounts,
        selectorField,
        selectorValue,
        inventory(inventoryAccountCode, inventoryField),
        expense(expenseAccountCode, expenseField));
  }

  private static void validatePair(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      AccountExpectation firstExpectation,
      AccountExpectation secondExpectation) {
    PostEntryRoleAccountSemantics.validatePair(
        violations, accounts, selectorField, selectorValue, firstExpectation, secondExpectation);
  }
}
