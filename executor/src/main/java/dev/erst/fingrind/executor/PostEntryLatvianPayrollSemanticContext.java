package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.core.AccountCode;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Resolves the complete declared-account set for one Latvian monthly-payroll request. */
final class PostEntryLatvianPayrollSemanticContext {
  private PostEntryLatvianPayrollSemanticContext() {}

  static Set<AccountCode> referencedAccounts(LatvianPayrollBookkeepingEntryVariants entry) {
    return switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll ->
          accountSet(
              payroll.wageExpenseAccountCode(),
              payroll.employerSocialContributionExpenseAccountCode(),
              payroll.netWagesPayableAccountCode(),
              payroll.employeeSocialContributionPayableAccountCode(),
              payroll.employerSocialContributionPayableAccountCode(),
              payroll.personalIncomeTaxPayableAccountCode());
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          settlementAccountSet(settlement.cashAccountCode(), settlement.resolvedSettlement());
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          settlementAccountSet(settlement.cashAccountCode(), settlement.resolvedSettlement());
    };
  }

  private static Set<AccountCode> settlementAccountSet(
      AccountCode cashAccountCode, @Nullable ResolvedLatvianPayrollSettlement settlement) {
    if (settlement == null) {
      return accountSet(cashAccountCode);
    }
    return accountSet(
        cashAccountCode,
        settlement.netWagesPayableAccountCode(),
        settlement.employeeSocialContributionPayableAccountCode(),
        settlement.employerSocialContributionPayableAccountCode(),
        settlement.personalIncomeTaxPayableAccountCode());
  }

  private static Set<AccountCode> accountSet(AccountCode... accountCodes) {
    Set<AccountCode> accounts = new LinkedHashSet<>();
    for (AccountCode accountCode : accountCodes) {
      accounts.add(accountCode);
    }
    return accounts;
  }
}
