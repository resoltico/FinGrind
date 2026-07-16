package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.StandardBookkeepingEntryVariants;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AccountCode;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Resolves referenced accounts for standard typed business events. */
final class PostEntryStandardSemanticContext {
  private PostEntryStandardSemanticContext() {}

  static Set<AccountCode> referencedAccounts(StandardBookkeepingEntryVariants entry) {
    return switch (entry) {
      case BookkeepingEntry.SaleSettled sale ->
          accountSet(
              sale.cashAccountCode(),
              sale.revenueAccountCode(),
              taxAccountCode(sale.appliedTax()),
              inventoryReliefAccountCode(sale.inventoryRelief(), true),
              inventoryReliefAccountCode(sale.inventoryRelief(), false));
      case BookkeepingEntry.SaleOnCredit sale ->
          accountSet(
              sale.receivableAccountCode(),
              sale.revenueAccountCode(),
              taxAccountCode(sale.appliedTax()),
              inventoryReliefAccountCode(sale.inventoryRelief(), true),
              inventoryReliefAccountCode(sale.inventoryRelief(), false));
      case BookkeepingEntry.PurchaseSettled purchase ->
          accountSet(
              purchase.inventoryAccountCode(),
              purchase.cashAccountCode(),
              taxAccountCode(purchase.appliedTax()));
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          accountSet(
              purchase.inventoryAccountCode(),
              purchase.payableAccountCode(),
              taxAccountCode(purchase.appliedTax()));
      case BookkeepingEntry.ExpenseSettled expense ->
          accountSet(
              expense.expenseAccountCode(),
              expense.cashAccountCode(),
              taxAccountCode(expense.appliedTax()));
      case BookkeepingEntry.ExpenseOnCredit expense ->
          accountSet(
              expense.expenseAccountCode(),
              expense.payableAccountCode(),
              taxAccountCode(expense.appliedTax()));
      case BookkeepingEntry.Receipt receipt ->
          accountSet(
              receipt.cashAccountCode(),
              receipt.receivableAccountCode(),
              receipt.settlementAdjunct() == null
                  ? null
                  : receipt.settlementAdjunct().accountCode());
      case BookkeepingEntry.Payment payment ->
          accountSet(
              payment.payableAccountCode(),
              payment.cashAccountCode(),
              payment.settlementAdjunct() == null
                  ? null
                  : payment.settlementAdjunct().accountCode());
      case BookkeepingEntry.OwnerContribution contribution ->
          accountSet(contribution.cashAccountCode(), contribution.equityAccountCode());
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          accountSet(withdrawal.equityAccountCode(), withdrawal.cashAccountCode());
    };
  }

  private static Set<AccountCode> accountSet(@Nullable AccountCode... accountCodes) {
    Set<AccountCode> accounts = new LinkedHashSet<>();
    for (AccountCode accountCode : accountCodes) {
      if (accountCode != null) {
        accounts.add(accountCode);
      }
    }
    return accounts;
  }

  private static @Nullable AccountCode taxAccountCode(@Nullable AppliedTax appliedTax) {
    return appliedTax == null ? null : appliedTax.taxAccountCode();
  }

  private static @Nullable AccountCode inventoryReliefAccountCode(
      dev.erst.fingrind.contract.bookkeeping.@Nullable InventoryRelief inventoryRelief,
      boolean inventoryAccount) {
    if (inventoryRelief == null) {
      return null;
    }
    return inventoryAccount
        ? inventoryRelief.inventoryAccountCode()
        : inventoryRelief.costOfSalesAccountCode();
  }
}
