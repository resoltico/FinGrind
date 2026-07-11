package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link Quantity}. */
class QuantityTest {
  @Test
  void ofScaledUnits_createsExactQuantityAndRejectsNegativeOrUnsupportedValues() {
    Quantity quantity = Quantity.ofScaledUnits(2, 123L);

    assertEquals(2, quantity.scale());
    assertEquals(123L, quantity.scaledUnits());
    assertEquals("1.23", quantity.canonicalDecimal());
    assertEquals(
        "Quantity scaled units must not be negative.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.ofScaledUnits(2, -1L))
            .getMessage());
    assertEquals(
        "Quantity scale must be between 0 and 9 inclusive.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.ofScaledUnits(-1, 1L))
            .getMessage());
    assertEquals(
        "Quantity scale must be between 0 and 9 inclusive.",
        assertThrows(
                IllegalArgumentException.class,
                () -> Quantity.ofScaledUnits(Quantity.maxSupportedScale() + 1, 1L))
            .getMessage());
  }

  @Test
  void parse_acceptsCanonicalValuesAcrossSupportedScales() {
    assertEquals("100", Quantity.parse(0, "100").canonicalDecimal());
    assertEquals("10.50", Quantity.parse(2, "10.5").canonicalDecimal());
    assertEquals("0.001", Quantity.parse(3, "0.001").canonicalDecimal());
  }

  @Test
  void canonicalDecimal_roundTripsAcrossSupportedScaleBuckets() {
    assertRoundTrip(0, 0L, 1L, 10L, 999L, 10_000L);
    assertRoundTrip(1, 0L, 1L, 10L, 105L, 12_345L, 100_000L);
    assertRoundTrip(3, 0L, 1L, 10L, 125L, 1_250L, 123_456L);
  }

  @Test
  void parse_rejectsMalformedShapesAndUnsupportedScaleSpecificPrecision() {
    assertEquals(
        "Quantity must not contain leading or trailing space.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, " 1.00"))
            .getMessage());
    assertEquals(
        "Quantity must not contain leading or trailing space.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "1.00 "))
            .getMessage());
    assertEquals(
        "Quantity must not be blank.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "")).getMessage());
    assertEquals(
        "Quantity must be non-negative and unsigned.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "+1.00"))
            .getMessage());
    assertEquals(
        "Quantity must be non-negative and unsigned.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "-1.00"))
            .getMessage());
    assertEquals(
        "Quantity must be a plain decimal string without exponent notation.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "1e3")).getMessage());
    assertEquals(
        "Quantity must contain at most one decimal point.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "1.0.0"))
            .getMessage());
    assertEquals(
        "Quantity must contain decimal digits.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, ".50")).getMessage());
    assertEquals(
        "Quantity must contain decimal digits.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "a1.00"))
            .getMessage());
    assertEquals(
        "Quantity must not end with a decimal point.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "1.")).getMessage());
    assertEquals(
        "Quantity must contain decimal digits only.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "1.0a")).getMessage());
    assertEquals(
        "Quantity must not contain redundant leading zeroes.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "001.00"))
            .getMessage());
    assertEquals(
        "Quantity must not contain fractional digits at scale 0.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(0, "1.01")).getMessage());
    assertEquals(
        "Quantity must use at most 2 fractional digits.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(2, "1.001"))
            .getMessage());
  }

  @Test
  void parse_rejectsAmountsOutsideSupportedExactScaledUnitRange() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> Quantity.parse(2, "92233720368547758.08"));

    assertEquals(
        "Quantity is outside the supported exact scaled-unit range.", exception.getMessage());
    assertEquals(
        "Quantity is outside the supported exact scaled-unit range.",
        assertThrows(
                IllegalArgumentException.class,
                () -> Quantity.parse(2, "1".repeat(Quantity.maxScaledUnitsDigitCount() + 1)))
            .getMessage());
  }

  @Test
  void zeroPlusMinusCompareAndBooleanQueriesFollowExactScaledUnitSemantics() {
    Quantity zero = Quantity.zero(2);
    Quantity oneAndHalf = Quantity.parse(2, "1.50");
    Quantity fiftyHundredths = Quantity.parse(2, "0.50");

    assertTrue(zero.isZero());
    assertFalse(zero.isPositive());
    assertFalse(oneAndHalf.isZero());
    assertTrue(oneAndHalf.isPositive());
    assertEquals("0.00", zero.canonicalDecimal());
    assertEquals(Quantity.parse(2, "2.00"), oneAndHalf.plus(fiftyHundredths));
    assertEquals(Quantity.parse(2, "1.00"), oneAndHalf.minus(fiftyHundredths));
    assertTrue(oneAndHalf.compareTo(fiftyHundredths) > 0);
    assertEquals(0, oneAndHalf.compareTo(Quantity.parse(2, "1.50")));
  }

  @Test
  void arithmeticAndComparisonRejectCrossScaleOrNegativeResults() {
    Quantity scaleZero = Quantity.parse(0, "1");
    Quantity scaleTwo = Quantity.parse(2, "1.00");

    assertEquals(
        "Quantity values must share one exact scale.",
        assertThrows(IllegalArgumentException.class, () -> scaleZero.plus(scaleTwo)).getMessage());
    assertEquals(
        "Quantity values must share one exact scale.",
        assertThrows(IllegalArgumentException.class, () -> scaleZero.minus(scaleTwo)).getMessage());
    assertEquals(
        "Quantity values must share one exact scale.",
        assertThrows(IllegalArgumentException.class, () -> scaleZero.compareTo(scaleTwo))
            .getMessage());
    assertEquals(
        "Quantity subtraction would produce a negative result.",
        assertThrows(
                IllegalArgumentException.class,
                () -> Quantity.parse(2, "1.00").minus(Quantity.parse(2, "2.00")))
            .getMessage());
  }

  @Test
  void equalityHashCodeAndToStringReflectScaleAndScaledUnits() {
    Quantity quantity = Quantity.parse(2, "1.23");
    Quantity same = Quantity.ofScaledUnits(2, 123L);

    assertEquals(quantity, quantity);
    assertEquals(quantity, same);
    assertEquals(quantity.hashCode(), same.hashCode());
    assertNotEquals(quantity, Quantity.parse(1, "1.2"));
    assertNotEquals(quantity, Quantity.parse(2, "1.24"));
    assertNotEquals(quantity, "1.23");
    assertEquals("Quantity[scale=2, scaledUnits=123, canonicalDecimal=1.23]", quantity.toString());
  }

  @Test
  void parseRejectsNullTextAndArithmeticRejectsNullArguments() {
    assertThrows(NullPointerException.class, () -> Quantity.parse(2, nullOf()));
    assertEquals(
        "Quantity scale must be between 0 and 9 inclusive.",
        assertThrows(IllegalArgumentException.class, () -> Quantity.parse(-1, "1")).getMessage());
    assertThrows(NullPointerException.class, () -> Quantity.zero(2).plus(nullOf()));
    assertThrows(NullPointerException.class, () -> Quantity.zero(2).minus(nullOf()));
    assertThrows(NullPointerException.class, () -> Quantity.zero(2).compareTo(nullOf()));
  }

  private static void assertRoundTrip(int scale, long... scaledUnits) {
    for (long value : scaledUnits) {
      Quantity quantity = Quantity.ofScaledUnits(scale, value);
      assertEquals(quantity, Quantity.parse(scale, quantity.canonicalDecimal()));
    }
  }
}
