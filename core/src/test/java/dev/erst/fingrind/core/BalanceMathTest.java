package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the bookkeeping-owned exact running-balance helpers. */
class BalanceMathTest {
  @Test
  void currencyBalanceAndHelpers_coverDebitCreditZeroAndAbsoluteRanges() {
    CurrencyUnit eur = CurrencyUnit.of("EUR");

    assertEquals(
        CurrencyBalance.ofTotals(Money.ofMinorUnits(eur, 15L), Money.ofMinorUnits(eur, 4L)),
        BalanceMath.currencyBalance(eur, 15L, 4L));
    assertEquals(BalanceSide.DEBIT, BalanceMath.balanceSide(11L));
    assertEquals(BalanceSide.CREDIT, BalanceMath.balanceSide(-11L));
    assertEquals(11L, BalanceMath.absoluteMinorUnits(11L));
    assertEquals(11L, BalanceMath.absoluteMinorUnits(-11L));
  }

  @Test
  void absoluteMinorUnitsRejectsUnsupportedOverflow() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> BalanceMath.absoluteMinorUnits(Long.MIN_VALUE));

    assertEquals(
        "Running balance exceeded the supported exact money range.", exception.getMessage());
  }

  @Test
  void balanceSideTreatsZeroAsNeutral() {
    assertEquals(BalanceSide.ZERO, BalanceMath.balanceSide(0L));
  }
}
