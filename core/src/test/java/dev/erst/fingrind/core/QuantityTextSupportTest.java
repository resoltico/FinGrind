package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for exact quantity-text boundary behavior. */
class QuantityTextSupportTest {
  @Test
  void canonicalizationValidatesTheScaleBeforeFormatting() {
    assertEquals("1.20", QuantityTextSupport.canonicalDecimal(120L, 2));
    assertEquals(
        "Quantity scale must be between 0 and 9 inclusive.",
        assertThrows(
                IllegalArgumentException.class, () -> QuantityTextSupport.canonicalDecimal(0L, -1))
            .getMessage());
  }
}
