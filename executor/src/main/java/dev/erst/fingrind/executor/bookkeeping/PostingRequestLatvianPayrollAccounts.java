package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.core.AccountCode;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Adds every payroll role account to a posting request's canonical account set. */
final class PostingRequestLatvianPayrollAccounts {
  private PostingRequestLatvianPayrollAccounts() {}

  static void add(Set<AccountCode> accounts, LatvianPayrollBookkeepingEntryVariants entry) {
    switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll -> {
        accounts.add(payroll.wageExpenseAccountCode());
        accounts.add(payroll.employerSocialContributionExpenseAccountCode());
        accounts.add(payroll.netWagesPayableAccountCode());
        accounts.add(payroll.employeeSocialContributionPayableAccountCode());
        accounts.add(payroll.employerSocialContributionPayableAccountCode());
        accounts.add(payroll.personalIncomeTaxPayableAccountCode());
      }
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          addSettlementAccounts(
              accounts, settlement.cashAccountCode(), settlement.resolvedSettlement());
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          addSettlementAccounts(
              accounts, settlement.cashAccountCode(), settlement.resolvedSettlement());
    }
  }

  private static void addSettlementAccounts(
      Set<AccountCode> accounts,
      AccountCode cashAccountCode,
      @Nullable ResolvedLatvianPayrollSettlement settlement) {
    accounts.add(cashAccountCode);
    if (settlement == null) {
      return;
    }
    accounts.add(settlement.netWagesPayableAccountCode());
    accounts.add(settlement.employeeSocialContributionPayableAccountCode());
    accounts.add(settlement.employerSocialContributionPayableAccountCode());
    accounts.add(settlement.personalIncomeTaxPayableAccountCode());
  }
}
