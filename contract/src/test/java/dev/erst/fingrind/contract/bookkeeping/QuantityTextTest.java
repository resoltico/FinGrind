package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import org.junit.jupiter.api.Test;

/** Coverage tests for exact quantity-text canonicalization and validation. */
class QuantityTextTest {
  @Test
  void canonicalizesTrailingZeroesAndResolvesAgainstUnitOfMeasure() {
    QuantityText trimmedFraction = new QuantityText("12.3400");
    QuantityText wholeUnits = new QuantityText("7.000");
    QuantityText zero = new QuantityText("0");

    assertEquals("12.34", trimmedFraction.value());
    assertEquals("7", wholeUnits.value());
    assertEquals("0", zero.value());
    assertFalse(trimmedFraction.isZero());
    assertTrue(zero.isZero());
    assertEquals(
        Quantity.ofScaledUnits(2, 1234), trimmedFraction.resolve(new UnitOfMeasure("unit", 2)));
  }

  @Test
  void rejectsInvalidQuantitySurfaceForms() {
    assertEquals(
        "Quantity text must not contain leading or trailing space.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText(" 1")).getMessage());
    assertEquals(
        "Quantity text must not be blank.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("")).getMessage());
    assertEquals(
        "Quantity text must be non-negative and unsigned.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("-1")).getMessage());
    assertEquals(
        "Quantity text must be non-negative and unsigned.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("+1")).getMessage());
    assertEquals(
        "Quantity text must be one plain decimal string without exponent notation.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("1e3")).getMessage());
    assertEquals(
        "Quantity text must be one plain decimal string without exponent notation.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("1E3")).getMessage());
    assertEquals(
        "Quantity text must contain at most one decimal point.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("1.2.3")).getMessage());
    assertEquals(
        "Quantity text must contain decimal digits.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText(".5")).getMessage());
    assertEquals(
        "Quantity text must contain decimal digits.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("1a")).getMessage());
    assertEquals(
        "Quantity text must contain decimal digits.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("/")).getMessage());
    assertEquals(
        "Quantity text must not contain redundant leading zeroes.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("01")).getMessage());
    assertEquals(
        "Quantity text must not end with a decimal point.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("1.")).getMessage());
    assertEquals(
        "Quantity text must contain decimal digits only.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("1.a")).getMessage());
    assertEquals(
        "Quantity text must contain decimal digits only.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("1.1a")).getMessage());
    assertEquals(
        "Quantity text must contain decimal digits only.",
        assertThrows(IllegalArgumentException.class, () -> new QuantityText("1./")).getMessage());
    assertEquals(
        "Quantity text is outside the supported exact range.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuantityText("1".repeat(Quantity.maxScaledUnitsDigitCount() + 1)))
            .getMessage());
    assertEquals(
        "Quantity text must use at most " + Quantity.maxSupportedScale() + " fractional digits.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuantityText("1." + "1".repeat(Quantity.maxSupportedScale() + 1)))
            .getMessage());
  }
}
