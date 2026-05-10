package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PositiveMoney}. */
class PositiveMoneyTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  @Test
  void of_acceptsPositiveAmount() {
    PositiveMoney amount = PositiveMoney.of(Money.parse("EUR", "12.50"));

    assertEquals(1_250L, amount.minorUnits());
    assertEquals("12.50", amount.canonicalDecimal());
  }

  @Test
  void of_rejectsZeroAmount() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> PositiveMoney.of(Money.zero(EUR)));

    assertEquals("Journal line amount must be greater than zero.", exception.getMessage());
  }

  @Test
  void parseAndAccessorsExposeTheUnderlyingExactMoneyValue() {
    PositiveMoney amount = PositiveMoney.parse(EUR, "12.50");

    assertEquals(Money.parse("EUR", "12.50"), amount.money());
    assertEquals(EUR, amount.currencyUnit());
    assertEquals(1_250L, amount.minorUnits());
    assertEquals("12.50", amount.canonicalDecimal());
  }

  @Test
  void equalityHashCodeAndToStringFollowUnderlyingMoneyValue() {
    PositiveMoney amount = PositiveMoney.parse(EUR, "12.50");
    PositiveMoney same = PositiveMoney.of(Money.ofMinorUnits(EUR, 1_250L));

    assertEquals(amount, amount);
    assertEquals(amount, same);
    assertEquals(amount.hashCode(), same.hashCode());
    assertNotEquals(amount, PositiveMoney.parse(EUR, "12.51"));
    assertNotEquals(amount, "12.50");
    assertEquals(
        "PositiveMoney[value=Money[currencyUnit=CurrencyUnit[code=EUR, minorUnitScale=2], minorUnits=1250, canonicalDecimal=12.50]]",
        amount.toString());
  }
}
