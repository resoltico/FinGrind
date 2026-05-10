package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link CurrencyBalance}. */
class CurrencyBalanceTest {
  @Test
  void ofTotals_derivesOneConsistentCurrencyBucket() {
    CurrencyBalance balance = CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "4.00"));

    assertEquals(money("EUR", "10.00"), balance.debitTotal());
    assertEquals(money("EUR", "4.00"), balance.creditTotal());
    assertEquals(money("EUR", "6.00"), balance.netAmount());
    assertEquals(BalanceSide.DEBIT, balance.balanceSide());
  }

  @Test
  void ofTotals_rejectsMixedCurrencies() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyBalance.ofTotals(money("EUR", "10.00"), money("USD", "4.00")));

    assertEquals("Currency balance totals must share one currency unit.", exception.getMessage());
  }

  @Test
  void ofTotals_derivesCreditAndZeroBalances() {
    CurrencyBalance creditBalance =
        CurrencyBalance.ofTotals(money("EUR", "4.00"), money("EUR", "10.00"));
    CurrencyBalance zeroBalance =
        CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "10.00"));

    assertEquals(money("EUR", "6.00"), creditBalance.netAmount());
    assertEquals(BalanceSide.CREDIT, creditBalance.balanceSide());
    assertEquals(money("EUR", "0.00"), zeroBalance.netAmount());
    assertEquals(BalanceSide.ZERO, zeroBalance.balanceSide());
  }

  @Test
  void equalityHashCodeAndToStringReflectDerivedBalanceState() {
    CurrencyBalance balance = CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "4.00"));
    CurrencyBalance same = CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "4.00"));

    assertEquals(balance, balance);
    assertEquals(balance, same);
    assertEquals(balance.hashCode(), same.hashCode());
    assertNotEquals(balance, CurrencyBalance.ofTotals(money("EUR", "9.00"), money("EUR", "4.00")));
    assertNotEquals(balance, CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "5.00")));
    assertNotEquals(balance, "balance");
    assertEquals(
        "CurrencyBalance[debitTotal=Money[currencyUnit=CurrencyUnit[code=EUR, minorUnitScale=2], minorUnits=1000, canonicalDecimal=10.00], creditTotal=Money[currencyUnit=CurrencyUnit[code=EUR, minorUnitScale=2], minorUnits=400, canonicalDecimal=4.00], netAmount=Money[currencyUnit=CurrencyUnit[code=EUR, minorUnitScale=2], minorUnits=600, canonicalDecimal=6.00], balanceSide=DEBIT]",
        balance.toString());
  }

  private static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
  }
}
