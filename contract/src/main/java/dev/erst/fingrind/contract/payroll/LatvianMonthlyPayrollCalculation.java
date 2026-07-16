package dev.erst.fingrind.contract.payroll;

import dev.erst.fingrind.core.Money;
import java.util.Objects;

/** Exact statutory component amounts for one admitted Latvian monthly payroll run. */
public record LatvianMonthlyPayrollCalculation(
    Money grossWages,
    Money employeeSocialContribution,
    Money employerSocialContribution,
    Money monthlyNonTaxableMinimum,
    Money personalIncomeTax,
    Money netWages) {
  /** Validates one internally coherent payroll calculation. */
  public LatvianMonthlyPayrollCalculation {
    Objects.requireNonNull(grossWages, "grossWages");
    Objects.requireNonNull(employeeSocialContribution, "employeeSocialContribution");
    Objects.requireNonNull(employerSocialContribution, "employerSocialContribution");
    Objects.requireNonNull(monthlyNonTaxableMinimum, "monthlyNonTaxableMinimum");
    Objects.requireNonNull(personalIncomeTax, "personalIncomeTax");
    Objects.requireNonNull(netWages, "netWages");
    requireCurrency(grossWages, employeeSocialContribution, "employeeSocialContribution");
    requireCurrency(grossWages, employerSocialContribution, "employerSocialContribution");
    requireCurrency(grossWages, monthlyNonTaxableMinimum, "monthlyNonTaxableMinimum");
    requireCurrency(grossWages, personalIncomeTax, "personalIncomeTax");
    requireCurrency(grossWages, netWages, "netWages");
    if (!grossWages.minus(employeeSocialContribution).minus(personalIncomeTax).equals(netWages)) {
      throw new IllegalArgumentException(
          "Net wages must equal gross wages less employee social contribution and personal income tax.");
    }
  }

  /** Returns the total employer cost booked by the payroll-origin posting. */
  public Money totalEmployerCost() {
    return grossWages.plus(employerSocialContribution);
  }

  /** Returns the state-remittance liability for withheld tax and both social components. */
  public Money stateRemittance() {
    return employeeSocialContribution.plus(employerSocialContribution).plus(personalIncomeTax);
  }

  private static void requireCurrency(Money expected, Money actual, String field) {
    if (!expected.currencyUnit().equals(actual.currencyUnit())) {
      throw new IllegalArgumentException(field + " must use the grossWages currency.");
    }
  }
}
