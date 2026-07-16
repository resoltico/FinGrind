package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.StandardBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.Set;

/** Adds standard typed event accounts to a posting request's canonical account set. */
final class PostingRequestStandardTypedEntryAccounts {
  private PostingRequestStandardTypedEntryAccounts() {}

  static void add(Set<AccountCode> accounts, StandardBookkeepingEntryVariants entry) {
    switch (entry) {
      case BookkeepingEntry.SaleSettled sale -> {
        accounts.add(sale.cashAccountCode());
        accounts.add(sale.revenueAccountCode());
        addInventoryRelief(accounts, sale.inventoryRelief());
      }
      case BookkeepingEntry.SaleOnCredit sale -> {
        accounts.add(sale.receivableAccountCode());
        accounts.add(sale.revenueAccountCode());
        addInventoryRelief(accounts, sale.inventoryRelief());
      }
      case BookkeepingEntry.PurchaseSettled purchase -> {
        accounts.add(purchase.inventoryAccountCode());
        accounts.add(purchase.cashAccountCode());
      }
      case BookkeepingEntry.PurchaseOnCredit purchase -> {
        accounts.add(purchase.inventoryAccountCode());
        accounts.add(purchase.payableAccountCode());
      }
      case BookkeepingEntry.ExpenseSettled expense -> {
        accounts.add(expense.expenseAccountCode());
        accounts.add(expense.cashAccountCode());
      }
      case BookkeepingEntry.ExpenseOnCredit expense -> {
        accounts.add(expense.expenseAccountCode());
        accounts.add(expense.payableAccountCode());
      }
      case BookkeepingEntry.Receipt receipt -> {
        accounts.add(receipt.cashAccountCode());
        accounts.add(receipt.receivableAccountCode());
        if (receipt.settlementAdjunct() != null) {
          accounts.add(receipt.settlementAdjunct().accountCode());
        }
      }
      case BookkeepingEntry.Payment payment -> {
        accounts.add(payment.payableAccountCode());
        accounts.add(payment.cashAccountCode());
        if (payment.settlementAdjunct() != null) {
          accounts.add(payment.settlementAdjunct().accountCode());
        }
      }
      case BookkeepingEntry.OwnerContribution contribution -> {
        accounts.add(contribution.cashAccountCode());
        accounts.add(contribution.equityAccountCode());
      }
      case BookkeepingEntry.OwnerWithdrawal withdrawal -> {
        accounts.add(withdrawal.equityAccountCode());
        accounts.add(withdrawal.cashAccountCode());
      }
    }
  }

  private static void addInventoryRelief(
      Set<AccountCode> accounts,
      dev.erst.fingrind.contract.bookkeeping.@org.jspecify.annotations.Nullable InventoryRelief
          inventoryRelief) {
    if (inventoryRelief == null) {
      return;
    }
    accounts.add(inventoryRelief.inventoryAccountCode());
    accounts.add(inventoryRelief.costOfSalesAccountCode());
  }
}
