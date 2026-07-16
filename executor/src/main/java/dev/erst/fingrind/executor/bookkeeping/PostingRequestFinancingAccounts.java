package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.Set;

/** Adds typed financing event accounts to a posting request's canonical account set. */
final class PostingRequestFinancingAccounts {
  private PostingRequestFinancingAccounts() {}

  static void add(Set<AccountCode> accounts, FinancingBookkeepingEntryVariants entry) {
    switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing borrowing -> {
        accounts.add(borrowing.cashAccountCode());
        accounts.add(borrowing.principalLiabilityAccountCode());
        accounts.add(borrowing.interestPayableAccountCode());
      }
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment -> {
        accounts.add(repayment.cashAccountCode());
        if (repayment.resolvedApplication() != null) {
          accounts.add(repayment.resolvedApplication().principalLiabilityAccountCode());
        }
      }
      case FinancingBookkeepingEntryVariants.InterestAccrual accrual -> {
        accounts.add(accrual.interestExpenseAccountCode());
        if (accrual.resolvedApplication() != null) {
          accounts.add(accrual.resolvedApplication().interestPayableAccountCode());
        }
      }
      case FinancingBookkeepingEntryVariants.InterestPayment payment -> {
        accounts.add(payment.cashAccountCode());
        if (payment.resolvedApplication() != null) {
          accounts.add(payment.resolvedApplication().interestPayableAccountCode());
        }
      }
    }
  }
}
