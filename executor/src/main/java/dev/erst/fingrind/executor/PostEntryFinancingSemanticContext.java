package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves referenced accounts for typed financing business events. */
final class PostEntryFinancingSemanticContext {
  private PostEntryFinancingSemanticContext() {}

  static Set<AccountCode> referencedAccounts(FinancingBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing borrowing ->
          accountSet(
              borrowing.cashAccountCode(),
              borrowing.principalLiabilityAccountCode(),
              borrowing.interestPayableAccountCode());
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment ->
          repayment.resolvedApplication() == null
              ? accountSet(repayment.cashAccountCode())
              : accountSet(
                  repayment.cashAccountCode(),
                  repayment.resolvedApplication().principalLiabilityAccountCode());
      case FinancingBookkeepingEntryVariants.InterestAccrual interestAccrual ->
          interestAccrual.resolvedApplication() == null
              ? accountSet(interestAccrual.interestExpenseAccountCode())
              : accountSet(
                  interestAccrual.interestExpenseAccountCode(),
                  interestAccrual.resolvedApplication().interestPayableAccountCode());
      case FinancingBookkeepingEntryVariants.InterestPayment interestPayment ->
          interestPayment.resolvedApplication() == null
              ? accountSet(interestPayment.cashAccountCode())
              : accountSet(
                  interestPayment.cashAccountCode(),
                  interestPayment.resolvedApplication().interestPayableAccountCode());
    };
  }

  private static Set<AccountCode> accountSet(AccountCode... accountCodes) {
    Set<AccountCode> accounts = new LinkedHashSet<>();
    java.util.Collections.addAll(accounts, accountCodes);
    return accounts;
  }
}
