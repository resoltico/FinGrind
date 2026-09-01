package dev.erst.fingrind.executor.bookkeeping.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for pure reporting balance and equation support. */
class ReportingBalanceSupportTest {
  @Test
  void signedBalance_preservesAZeroBalanceAsNeitherDebitNorCredit() {
    CurrencyUnit currencyUnit = CurrencyUnit.of("EUR");

    assertEquals(
        BalanceSide.ZERO, ReportingBalanceSupport.signedBalance(currencyUnit, 0L).balanceSide());
    assertEquals(
        BalanceSide.CREDIT, ReportingBalanceSupport.signedBalance(currencyUnit, 1L).balanceSide());
    assertEquals(
        BalanceSide.DEBIT, ReportingBalanceSupport.signedBalance(currencyUnit, -1L).balanceSide());
  }

  @Test
  void financialPositionEquation_requiresEveryOwnedSection() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> FinancialPositionEquationSupport.assertAccountingEquation(List.of()));

    assertEquals("Missing statement-of-financial-position section: ASSET", failure.getMessage());
  }
}
