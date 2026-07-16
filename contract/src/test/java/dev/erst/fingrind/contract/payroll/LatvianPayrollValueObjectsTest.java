package dev.erst.fingrind.contract.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.Money;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Boundary coverage for the published Latvian payroll identifiers, calendar values, and totals. */
class LatvianPayrollValueObjectsTest {
  @Test
  void identifiersAcceptCanonicalValuesAndRejectInvalidInput() {
    assertEquals(
        "payroll-run-2026-07-employee-001",
        new LatvianPayrollRunId(" payroll-run-2026-07-employee-001 ").value());
    assertEquals("[a-z0-9]+(?:-[a-z0-9]+)*", LatvianPayrollRunId.pattern());
    assertEquals(120, LatvianPayrollRunId.maxLength());
    assertThrows(IllegalArgumentException.class, () -> new LatvianPayrollRunId(" "));
    assertThrows(IllegalArgumentException.class, () -> new LatvianPayrollRunId("UPPERCASE"));
    assertThrows(IllegalArgumentException.class, () -> new LatvianPayrollRunId("a".repeat(121)));

    assertEquals("employee-001", new LatvianPayrollEmployeeReference("employee-001").value());
    assertEquals("[a-z0-9][a-z0-9-]{0,119}", LatvianPayrollEmployeeReference.pattern());
    assertEquals(120, LatvianPayrollEmployeeReference.maxLength());
    assertThrows(
        IllegalArgumentException.class, () -> new LatvianPayrollEmployeeReference("Employee 001"));
  }

  @Test
  void monthAndSettlementVocabularyRoundTripAndRejectInvalidWireValues() {
    LatvianPayrollMonth month = LatvianPayrollMonth.parse("2026-07");

    assertEquals(YearMonth.of(2026, 7), month.value());
    assertEquals("2026-07", month.wireValue());
    assertEquals("[0-9]{4}-(0[1-9]|1[0-2])", LatvianPayrollMonth.wirePattern());
    assertEquals(7, LatvianPayrollMonth.wireLength());
    assertThrows(IllegalArgumentException.class, () -> LatvianPayrollMonth.parse("2026-13"));

    assertEquals(
        java.util.List.of("NET_WAGES", "STATE_REMITTANCE"),
        LatvianPayrollSettlementKind.wireValues());
    assertEquals(
        LatvianPayrollSettlementKind.NET_WAGES,
        LatvianPayrollSettlementKind.fromWireValue("NET_WAGES"));
    assertThrows(
        IllegalArgumentException.class,
        () -> LatvianPayrollSettlementKind.fromWireValue("unrecognised"));
  }

  @Test
  void calculationRejectsInvalidCurrencyAndTotalsAndLeavesOnlyExactDerivedTotals() {
    Money eur100 = Money.parse("EUR", "100.00");
    Money usd100 = Money.parse("USD", "100.00");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            LatvianMonthlyPayroll2026.calculate(
                LatvianPayrollMonth.parse("2026-07"), Money.zero(eur100.currencyUnit())));
    assertThrows(
        IllegalArgumentException.class,
        () -> LatvianMonthlyPayroll2026.calculate(LatvianPayrollMonth.parse("2025-12"), eur100));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianMonthlyPayrollCalculation(
                eur100,
                Money.parse("EUR", "10.50"),
                Money.parse("EUR", "23.59"),
                Money.parse("EUR", "89.50"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "90.00")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianMonthlyPayrollCalculation(
                eur100,
                Money.parse("USD", "10.50"),
                Money.parse("EUR", "23.59"),
                Money.parse("EUR", "89.50"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "89.50")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianMonthlyPayrollCalculation(
                eur100,
                Money.parse("EUR", "10.50"),
                Money.parse("EUR", "23.59"),
                Money.parse("EUR", "89.50"),
                Money.parse("USD", "0.00"),
                Money.parse("EUR", "89.50")));

    LatvianMonthlyPayrollCalculation calculation =
        new LatvianMonthlyPayrollCalculation(
            eur100,
            Money.parse("EUR", "10.50"),
            Money.parse("EUR", "23.59"),
            Money.parse("EUR", "89.50"),
            Money.parse("EUR", "0.00"),
            Money.parse("EUR", "89.50"));
    assertEquals("123.59", calculation.totalEmployerCost().canonicalDecimal());
    assertEquals("34.09", calculation.stateRemittance().canonicalDecimal());
    assertEquals("100.00", usd100.canonicalDecimal());
  }
}
