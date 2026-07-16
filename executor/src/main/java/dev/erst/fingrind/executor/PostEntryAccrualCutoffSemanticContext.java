package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves referenced accounts for typed accrual cut-off business events. */
final class PostEntryAccrualCutoffSemanticContext {
  private PostEntryAccrualCutoffSemanticContext() {}

  static Set<AccountCode> referencedAccounts(AccrualCutoffBookkeepingEntryVariants entry) {
    return switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
          accountSet(
              prepayment.prepaymentAssetAccountCode(),
              prepayment.expenseAccountCode(),
              prepayment.cashAccountCode());
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
          accountSet(
              deferredRevenue.cashAccountCode(),
              deferredRevenue.deferredRevenueAccountCode(),
              deferredRevenue.revenueAccountCode());
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
          accountSet(
              accruedExpense.expenseAccountCode(),
              accruedExpense.accruedExpenseLiabilityAccountCode());
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
          recognition.resolvedApplication() == null
              ? Set.of()
              : accountSet(
                  recognition.resolvedApplication().debitAccountCode(),
                  recognition.resolvedApplication().creditAccountCode());
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
          settlement.resolvedApplication() == null
              ? accountSet(settlement.cashAccountCode())
              : accountSet(
                  settlement.resolvedApplication().debitAccountCode(),
                  settlement.resolvedApplication().creditAccountCode());
    };
  }

  private static Set<AccountCode> accountSet(AccountCode... accountCodes) {
    Set<AccountCode> accounts = new LinkedHashSet<>();
    for (AccountCode accountCode : accountCodes) {
      accounts.add(accountCode);
    }
    return accounts;
  }
}
