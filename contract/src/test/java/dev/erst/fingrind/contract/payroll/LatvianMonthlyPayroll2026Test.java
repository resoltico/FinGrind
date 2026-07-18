package dev.erst.fingrind.contract.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.Money;
import org.junit.jupiter.api.Test;

/**
 * Verifies exact calculation and boundary rejection for the supported Latvian 2026 payroll profile.
 */
class LatvianMonthlyPayroll2026Test {
  @Test
  void calculatesTheNarrowStandardMonthlyModelFromExactMinorUnits() {
    LatvianMonthlyPayrollCalculation calculation =
        LatvianMonthlyPayroll2026.calculate(
            LatvianPayrollMonth.parse("2026-07"),
            Money.parse("EUR", "2000.00"),
            LatvianPayrollWithholdingProfile.taxBookWithNoDependantsFor2026());

    assertEquals("210.00", calculation.employeeSocialContribution().canonicalDecimal());
    assertEquals("471.80", calculation.employerSocialContribution().canonicalDecimal());
    assertEquals("550.00", calculation.monthlyNonTaxableMinimum().canonicalDecimal());
    assertEquals("316.20", calculation.personalIncomeTax().canonicalDecimal());
    assertEquals("1473.80", calculation.netWages().canonicalDecimal());
    assertEquals("2471.80", calculation.totalEmployerCost().canonicalDecimal());
    assertEquals("998.00", calculation.stateRemittance().canonicalDecimal());
  }

  @Test
  void capsTheNonTaxableMinimumAtIncomeAfterEmployeeSocialContribution() {
    LatvianMonthlyPayrollCalculation calculation =
        LatvianMonthlyPayroll2026.calculate(
            LatvianPayrollMonth.parse("2026-01"),
            Money.parse("EUR", "100.00"),
            LatvianPayrollWithholdingProfile.taxBookWithNoDependantsFor2026());

    assertEquals("10.50", calculation.employeeSocialContribution().canonicalDecimal());
    assertEquals("89.50", calculation.monthlyNonTaxableMinimum().canonicalDecimal());
    assertEquals("0.00", calculation.personalIncomeTax().canonicalDecimal());
  }

  @Test
  void rejectsAmountsAndPeriodsOutsideTheOwnedModel() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LatvianMonthlyPayroll2026.calculate(
                LatvianPayrollMonth.parse("2027-01"),
                Money.parse("EUR", "1000.00"),
                LatvianPayrollWithholdingProfile.taxBookWithNoDependantsFor2026()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LatvianMonthlyPayroll2026.calculate(
                LatvianPayrollMonth.parse("2026-01"),
                Money.parse("USD", "1000.00"),
                LatvianPayrollWithholdingProfile.taxBookWithNoDependantsFor2026()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LatvianMonthlyPayroll2026.calculate(
                LatvianPayrollMonth.parse("2026-01"),
                Money.parse("EUR", "8775.01"),
                LatvianPayrollWithholdingProfile.taxBookWithNoDependantsFor2026()));
  }

  @Test
  void rejectsWithholdingProfilesOutsideTheOwnedStatutoryCalculation() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LatvianMonthlyPayroll2026.calculate(
                LatvianPayrollMonth.parse("2026-07"),
                Money.parse("EUR", "2000.00"),
                new LatvianPayrollWithholdingProfile(false, 0)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LatvianMonthlyPayroll2026.calculate(
                LatvianPayrollMonth.parse("2026-07"),
                Money.parse("EUR", "2000.00"),
                new LatvianPayrollWithholdingProfile(true, 1)));
  }
}
