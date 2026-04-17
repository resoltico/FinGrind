package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PositiveMoney}. */
class PositiveMoneyTest {
  @Test
  void constructor_acceptsPositiveAmount() {
    PositiveMoney amount = new PositiveMoney(new CurrencyCode("EUR"), new BigDecimal("12.50"));

    assertEquals(new BigDecimal("12.5"), amount.amount());
  }

  @Test
  void constructor_rejectsZeroAmount() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new PositiveMoney(new CurrencyCode("EUR"), BigDecimal.ZERO));

    assertEquals("Journal line amount must be greater than zero.", exception.getMessage());
  }
}
