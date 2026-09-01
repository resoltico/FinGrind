package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for the shared exact-decimal parsing and canonicalization rules. */
class ExactDecimalTextSupportTest {
  @Test
  void rejectsExponentMarkersAtTheFirstCharacter() {
    assertExponentRejected("e1");
    assertExponentRejected("E1");
  }

  @Test
  void separatesFractionalTextAtTheLeadingDecimalPointAndWithoutOne() {
    ExactDecimalTextSupport.DecimalParts leadingPoint =
        ExactDecimalTextSupport.splitDecimalText(".5", 0);
    ExactDecimalTextSupport.DecimalParts wholeOnly =
        ExactDecimalTextSupport.splitDecimalText("12", -1);

    assertEquals("", leadingPoint.wholeUnitsText());
    assertEquals("5", leadingPoint.fractionalText());
    assertEquals("12", wholeOnly.wholeUnitsText());
    assertEquals("", wholeOnly.fractionalText());
  }

  @Test
  void preservesExactBoundaryDigitsAndCanonicalFractionPadding() {
    assertDoesNotThrow(
        () ->
            ExactDecimalTextSupport.validateWholeUnits(
                "amount",
                "1".repeat(ExactDecimalTextSupport.maxScaledIntegerDigitCount()),
                "range"));
    assertEquals(
        "range",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ExactDecimalTextSupport.validateWholeUnits(
                        "amount",
                        "1".repeat(ExactDecimalTextSupport.maxScaledIntegerDigitCount() + 1),
                        "range"))
            .getMessage());
    assertEquals("1.20", ExactDecimalTextSupport.canonicalDecimal(120L, 2));
  }

  @Test
  void acceptsZeroFractionalDigitsAndRejectsCharactersBelowTheDigitRange() {
    assertDoesNotThrow(
        () ->
            ExactDecimalTextSupport.validateFractionalText(
                "amount", "0", true, 1, "scale", ignored -> "precision"));
    assertDoesNotThrow(
        () ->
            ExactDecimalTextSupport.validateFractionalText(
                "amount", "9", true, 1, "scale", ignored -> "precision"));
    assertEquals(
        "amount must contain decimal digits only.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ExactDecimalTextSupport.validateFractionalText(
                        "amount", "/", true, 1, "scale", ignored -> "precision"))
            .getMessage());
  }

  private static void assertExponentRejected(String decimalText) {
    assertEquals(
        "amount must be a plain decimal string without exponent notation.",
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactDecimalTextSupport.requireSupportedDecimalText("amount", decimalText))
            .getMessage());
  }
}
