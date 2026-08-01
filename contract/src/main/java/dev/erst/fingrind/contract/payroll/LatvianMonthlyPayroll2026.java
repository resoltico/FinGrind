package dev.erst.fingrind.contract.payroll;

import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import java.math.BigInteger;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Exact calculation owner for the narrow 2026 Latvian resident monthly-employment payroll model.
 *
 * <p>The model admits only an ordinary employee insured for all social-insurance types, whose
 * payroll tax book is held by this sole employer and who has no dependants. Pension recipients,
 * disability or service-pension rates, foreign employment, corrections, and annual progressive-rate
 * cases are intentionally outside this owner.
 */
public final class LatvianMonthlyPayroll2026 {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");
  private static final YearMonth FIRST_MONTH = YearMonth.of(2026, 1);
  private static final YearMonth LAST_MONTH = YearMonth.of(2026, 12);
  private static final int PARTS_PER_MILLION = 1_000_000;
  private static final int EMPLOYEE_SOCIAL_RATE = 105_000;
  private static final int EMPLOYER_SOCIAL_RATE = 235_900;
  private static final int SALARY_TAX_RATE = 255_000;
  private static final long MONTHLY_NON_TAXABLE_MINIMUM_MINOR_UNITS = 55_000L;
  private static final long MONTHLY_PROGRESSIVE_THRESHOLD_MINOR_UNITS = 877_500L;

  private LatvianMonthlyPayroll2026() {}

  /** Calculates one payroll run from exact gross monthly wages and declared withholding facts. */
  public static LatvianMonthlyPayrollCalculation calculate(
      LatvianPayrollMonth payrollMonth,
      Money grossWages,
      LatvianPayrollWithholdingProfile withholdingProfile) {
    Objects.requireNonNull(payrollMonth, "payrollMonth");
    Objects.requireNonNull(grossWages, "grossWages");
    Objects.requireNonNull(withholdingProfile, "withholdingProfile");
    requireAdmittedMonth(payrollMonth);
    requireEur(grossWages);
    withholdingProfile.requireSupported2026Profile();
    if (!grossWages.isPositive()) {
      throw new IllegalArgumentException("Gross wages must be positive.");
    }
    if (grossWages.minorUnits() > MONTHLY_PROGRESSIVE_THRESHOLD_MINOR_UNITS) {
      throw new IllegalArgumentException(
          "Gross wages exceed the admitted monthly threshold for the 2026 standard payroll model.");
    }
    Money employeeSocial = proportion(grossWages, EMPLOYEE_SOCIAL_RATE);
    Money employerSocial = proportion(grossWages, EMPLOYER_SOCIAL_RATE);
    Money taxableIncome = grossWages.minus(employeeSocial);
    Money nonTaxableMinimum =
        Money.ofMinorUnits(
            EUR, Math.min(taxableIncome.minorUnits(), MONTHLY_NON_TAXABLE_MINIMUM_MINOR_UNITS));
    taxableIncome = taxableIncome.minus(nonTaxableMinimum);
    Money personalIncomeTax = proportion(taxableIncome, SALARY_TAX_RATE);
    Money netWages = grossWages.minus(employeeSocial).minus(personalIncomeTax);
    return new LatvianMonthlyPayrollCalculation(
        grossWages,
        employeeSocial,
        employerSocial,
        nonTaxableMinimum,
        personalIncomeTax,
        netWages,
        withholdingProfile);
  }

  private static Money proportion(Money amount, int ratePartsPerMillion) {
    BigInteger numerator =
        BigInteger.valueOf(amount.minorUnits()).multiply(BigInteger.valueOf(ratePartsPerMillion));
    BigInteger divisor = BigInteger.valueOf(PARTS_PER_MILLION);
    BigInteger rounded = numerator.add(divisor.divide(BigInteger.TWO)).divide(divisor);
    return Money.ofMinorUnits(EUR, rounded.longValueExact());
  }

  private static void requireAdmittedMonth(LatvianPayrollMonth payrollMonth) {
    YearMonth value = payrollMonth.value();
    if (value.isBefore(FIRST_MONTH) || value.isAfter(LAST_MONTH)) {
      throw new IllegalArgumentException(
          "The Latvian monthly payroll model currently admits only payroll months in 2026.");
    }
  }

  private static void requireEur(Money grossWages) {
    if (!EUR.equals(grossWages.currencyUnit())) {
      throw new IllegalArgumentException(
          "The Latvian monthly payroll model requires EUR gross wages.");
    }
  }
}
