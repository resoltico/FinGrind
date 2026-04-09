package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link CurrencyCode}. */
class CurrencyCodeTest {
  @Test
  void constructor_normalizesToUppercase() {
    CurrencyCode currencyCode = new CurrencyCode(" eur ");

    assertEquals("EUR", currencyCode.value());
  }

  @Test
  void constructor_rejectsInvalidCode() {
    assertThrows(IllegalArgumentException.class, () -> new CurrencyCode("EURO"));
  }
}
