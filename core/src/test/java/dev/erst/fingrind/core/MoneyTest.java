package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Money}. */
class MoneyTest {
  @Test
  void constructor_normalizesTrailingZeros() {
    Money money = new Money(new CurrencyCode("EUR"), new BigDecimal("1000.0"));

    assertEquals(new BigDecimal("1000"), money.amount());
  }

  @Test
  void constructor_rejectsNegativeAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Money(new CurrencyCode("EUR"), new BigDecimal("-1.00")));
  }
}
