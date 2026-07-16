package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.util.Objects;

/** Executor-resolved, exact payment facts for one immutable Latvian payroll run obligation. */
public record ResolvedLatvianPayrollSettlement(
    LatvianPayrollSettlementKind settlementKind,
    LatvianPayrollRunId payrollRunId,
    AccountCode cashAccountCode,
    AccountCode netWagesPayableAccountCode,
    AccountCode employeeSocialContributionPayableAccountCode,
    AccountCode employerSocialContributionPayableAccountCode,
    AccountCode personalIncomeTaxPayableAccountCode,
    Money netWages,
    Money employeeSocialContribution,
    Money employerSocialContribution,
    Money personalIncomeTax) {
  /** Validates the immutable payroll components used to derive the settlement journal. */
  public ResolvedLatvianPayrollSettlement {
    Objects.requireNonNull(settlementKind, "settlementKind");
    Objects.requireNonNull(payrollRunId, "payrollRunId");
    Objects.requireNonNull(cashAccountCode, "cashAccountCode");
    Objects.requireNonNull(netWagesPayableAccountCode, "netWagesPayableAccountCode");
    Objects.requireNonNull(
        employeeSocialContributionPayableAccountCode,
        "employeeSocialContributionPayableAccountCode");
    Objects.requireNonNull(
        employerSocialContributionPayableAccountCode,
        "employerSocialContributionPayableAccountCode");
    Objects.requireNonNull(
        personalIncomeTaxPayableAccountCode, "personalIncomeTaxPayableAccountCode");
    Objects.requireNonNull(netWages, "netWages");
    Objects.requireNonNull(employeeSocialContribution, "employeeSocialContribution");
    Objects.requireNonNull(employerSocialContribution, "employerSocialContribution");
    Objects.requireNonNull(personalIncomeTax, "personalIncomeTax");
    if (!netWages.currencyUnit().equals(employeeSocialContribution.currencyUnit())
        || !netWages.currencyUnit().equals(employerSocialContribution.currencyUnit())
        || !netWages.currencyUnit().equals(personalIncomeTax.currencyUnit())) {
      throw new IllegalArgumentException(
          "Resolved Latvian payroll settlement components must share one currency.");
    }
    if (!netWages.isPositive()) {
      throw new IllegalArgumentException("Resolved Latvian payroll net wages must be positive.");
    }
  }

  /** Returns the exact amount payable to the State Revenue Service for this run. */
  public Money stateRemittance() {
    return employeeSocialContribution.plus(employerSocialContribution).plus(personalIncomeTax);
  }
}
