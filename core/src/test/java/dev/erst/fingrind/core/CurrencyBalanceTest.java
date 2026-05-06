package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CurrencyBalance}. */
class CurrencyBalanceTest {
  @Test
  void constructor_acceptsOneConsistentCurrencyBucket() {
    CurrencyBalance balance =
        new CurrencyBalance(
            money("EUR", "10.00"), money("EUR", "4.00"), money("EUR", "6.00"), BalanceSide.DEBIT);

    assertEquals(money("EUR", "10.00"), balance.debitTotal());
    assertEquals(money("EUR", "4.00"), balance.creditTotal());
    assertEquals(money("EUR", "6.00"), balance.netAmount());
    assertEquals(BalanceSide.DEBIT, balance.balanceSide());
  }

  @Test
  void constructor_rejectsMixedCurrencies() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CurrencyBalance(
                    money("EUR", "10.00"),
                    money("USD", "4.00"),
                    money("EUR", "6.00"),
                    BalanceSide.DEBIT));

    assertEquals("Currency balance totals must share one currencyCode.", exception.getMessage());
  }

  @Test
  void constructor_rejectsNetAmountWithDifferentCurrency() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CurrencyBalance(
                    money("EUR", "10.00"),
                    money("EUR", "4.00"),
                    money("USD", "6.00"),
                    BalanceSide.DEBIT));

    assertEquals("Currency balance totals must share one currencyCode.", exception.getMessage());
  }

  private static Money money(String currencyCode, String amount) {
    return new Money(new CurrencyCode(currencyCode), new BigDecimal(amount));
  }
}
