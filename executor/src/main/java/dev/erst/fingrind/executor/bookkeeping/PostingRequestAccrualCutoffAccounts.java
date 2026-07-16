package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.Set;

/** Adds typed accrual cut-off event accounts to a posting request's canonical account set. */
final class PostingRequestAccrualCutoffAccounts {
  private PostingRequestAccrualCutoffAccounts() {}

  static void add(Set<AccountCode> accounts, AccrualCutoffBookkeepingEntryVariants entry) {
    switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment -> {
        accounts.add(prepayment.prepaymentAssetAccountCode());
        accounts.add(prepayment.expenseAccountCode());
        accounts.add(prepayment.cashAccountCode());
      }
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue -> {
        accounts.add(deferredRevenue.cashAccountCode());
        accounts.add(deferredRevenue.deferredRevenueAccountCode());
        accounts.add(deferredRevenue.revenueAccountCode());
      }
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense -> {
        accounts.add(accruedExpense.expenseAccountCode());
        accounts.add(accruedExpense.accruedExpenseLiabilityAccountCode());
      }
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition -> {
        if (recognition.resolvedApplication() != null) {
          accounts.add(recognition.resolvedApplication().debitAccountCode());
          accounts.add(recognition.resolvedApplication().creditAccountCode());
        }
      }
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement -> {
        accounts.add(settlement.cashAccountCode());
        if (settlement.resolvedApplication() != null) {
          accounts.add(settlement.resolvedApplication().debitAccountCode());
          accounts.add(settlement.resolvedApplication().creditAccountCode());
        }
      }
    }
  }
}
