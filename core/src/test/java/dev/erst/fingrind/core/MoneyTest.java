package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link Money}. */
class MoneyTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  @Test
  void ofMinorUnits_createsExactMoneyAndRejectsNegativeMinorUnits() {
    Money money = Money.ofMinorUnits(EUR, 123L);

    assertEquals(EUR, money.currencyUnit());
    assertEquals(123L, money.minorUnits());
    assertEquals(2, money.scale());
    assertEquals("1.23", money.canonicalDecimal());
    assertEquals(
        "Money minor units must not be negative.",
        assertThrows(IllegalArgumentException.class, () -> Money.ofMinorUnits(EUR, -1L))
            .getMessage());
  }

  @Test
  void parse_acceptsCanonicalMinorUnitScaleAmount() {
    Money money = Money.parse("EUR", "1000.00");

    assertEquals(100_000L, money.minorUnits());
    assertEquals("1000.00", money.canonicalDecimal());
  }

  @Test
  void parse_acceptsZeroAmount() {
    Money money = Money.parse("EUR", "0.00");

    assertEquals(0L, money.minorUnits());
    assertEquals("0.00", money.canonicalDecimal());
  }

  @Test
  void parse_acceptsCanonicalAmountsAcrossSupportedCurrencyScales() {
    assertEquals("10.50", Money.parse("EUR", "10.5").canonicalDecimal());
    assertEquals("100", Money.parse("JPY", "100").canonicalDecimal());
    assertEquals("0.001", Money.parse("BHD", "0.001").canonicalDecimal());
  }

  @Test
  void canonicalDecimal_roundTripsAcrossSupportedCurrencyScaleBuckets() {
    assertRoundTrip("JPY", 0L, 1L, 10L, 999L, 10_000L);
    assertRoundTrip("EUR", 0L, 1L, 10L, 105L, 12_345L, 100_000L);
    assertRoundTrip("BHD", 0L, 1L, 10L, 125L, 1_250L, 123_456L);
  }

  @Test
  void parse_rejectsNegativeAmount() {
    assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "-1.00"));
  }

  @Test
  void parse_rejectsUnsupportedCurrencyCode() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> Money.parse("ZZZ", "1.00"));

    assertEquals("Unsupported currency unit code: ZZZ.", exception.getMessage());
  }

  @Test
  void parse_rejectsExponentNotation() {
    assertEquals(
        "Money amount must be a plain decimal string without exponent notation.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "1e3")).getMessage());
    assertEquals(
        "Money amount must be a plain decimal string without exponent notation.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "1E3")).getMessage());
  }

  @Test
  void parse_rejectsRedundantLeadingZeroes() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "001.00"));

    assertEquals("Money amount must not contain redundant leading zeroes.", exception.getMessage());
  }

  @Test
  void parse_rejectsWhitespaceSignedAndMalformedPlainDecimalShapes() {
    assertEquals(
        "Money amount must not contain leading or trailing space.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", " 1.00"))
            .getMessage());
    assertEquals(
        "Money amount must not contain leading or trailing space.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "1.00 "))
            .getMessage());
    assertEquals(
        "Money amount must not be blank.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "")).getMessage());
    assertEquals(
        "Money amount must be non-negative and unsigned.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "+1.00"))
            .getMessage());
    assertEquals(
        "Money amount must contain at most one decimal point.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "1.0.0"))
            .getMessage());
    assertEquals(
        "Money amount must contain decimal digits.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", ".50")).getMessage());
    assertEquals(
        "Money amount must contain decimal digits.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "a1.00"))
            .getMessage());
    assertEquals(
        "Money amount must contain decimal digits.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "/1.00"))
            .getMessage());
    assertEquals(
        "Money amount must not end with a decimal point.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "1.")).getMessage());
    assertEquals(
        "Money amount must contain decimal digits only.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "1.0a"))
            .getMessage());
    assertEquals(
        "Money amount must contain decimal digits only.",
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "1.0/"))
            .getMessage());
  }

  @Test
  void parse_rejectsUnsupportedFractionalDigitsForCurrency() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> Money.parse("JPY", "1.01"));

    assertEquals(
        "Money amount for JPY must not contain fractional digits.", exception.getMessage());
  }

  @Test
  void parse_rejectsFractionalDigitsBeyondCurrencyScale() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> Money.parse("EUR", "1.001"));

    assertEquals(
        "Money amount for EUR must use at most 2 fractional digits.", exception.getMessage());
  }

  @Test
  void parse_rejectsAmountsOutsideSupportedExactMinorUnitRange() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> Money.parse("EUR", "92233720368547758.08"));

    assertEquals(
        "Money amount is outside the supported exact minor-unit range.", exception.getMessage());
  }

  @Test
  void parse_rejectsWholeUnitsBeyondSupportedExactMinorUnitRange() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> Money.parse("EUR", "9223372036854775808"));

    assertEquals(
        "Money amount is outside the supported exact minor-unit range.", exception.getMessage());
  }

  @Test
  void parse_rejectsWholeUnitTextLongerThanTheSupportedExactMinorUnitDigitBound() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> Money.parse("EUR", "1".repeat(Money.maxMinorUnitsDigitCount() + 1)));

    assertEquals(
        "Money amount is outside the supported exact minor-unit range.", exception.getMessage());
  }

  @Test
  void zeroPlusMinusCompareAndBooleanQueriesFollowExactMinorUnitSemantics() {
    Money zero = Money.zero(EUR);
    Money oneEuro = Money.parse("EUR", "1.00");
    Money fiftyCents = Money.parse("EUR", "0.50");

    assertTrue(zero.isZero());
    assertFalse(zero.isPositive());
    assertFalse(oneEuro.isZero());
    assertTrue(oneEuro.isPositive());
    assertEquals("0.00", zero.canonicalDecimal());
    assertEquals(Money.parse("EUR", "1.50"), oneEuro.plus(fiftyCents));
    assertEquals(fiftyCents, oneEuro.minus(fiftyCents));
    assertTrue(oneEuro.compareTo(fiftyCents) > 0);
    assertEquals(0, oneEuro.compareTo(Money.parse("EUR", "1.00")));
  }

  @Test
  void arithmeticAndComparisonRejectCrossCurrencyOrNegativeResults() {
    Money euro = Money.parse("EUR", "1.00");
    Money usd = Money.parse("USD", "1.00");

    assertEquals(
        "Money values must share one currency unit.",
        assertThrows(IllegalArgumentException.class, () -> euro.plus(usd)).getMessage());
    assertEquals(
        "Money values must share one currency unit.",
        assertThrows(IllegalArgumentException.class, () -> euro.minus(usd)).getMessage());
    assertEquals(
        "Money values must share one currency unit.",
        assertThrows(IllegalArgumentException.class, () -> euro.compareTo(usd)).getMessage());
    assertEquals(
        "Money subtraction would produce a negative result.",
        assertThrows(IllegalArgumentException.class, () -> euro.minus(Money.parse("EUR", "2.00")))
            .getMessage());
    assertEquals(
        "other",
        assertThrows(NullPointerException.class, () -> euro.plus(nullOf(Money.class)))
            .getMessage());
  }

  @Test
  void equalityHashCodeAndToStringReflectCurrencyUnitAndMinorUnits() {
    Money money = Money.parse("EUR", "12.34");
    Money same = Money.ofMinorUnits(EUR, 1_234L);

    assertEquals(money, money);
    assertEquals(money, same);
    assertEquals(money.hashCode(), same.hashCode());
    assertNotEquals(money, Money.parse("EUR", "12.35"));
    assertNotEquals(money, Money.parse("USD", "12.34"));
    assertNotEquals(money, "12.34");
    assertEquals(
        "Money[currencyUnit=CurrencyUnit[code=EUR, minorUnitScale=2], minorUnits=1234, canonicalDecimal=12.34]",
        money.toString());
  }

  private static void assertRoundTrip(String currencyCode, long... minorUnitsSamples) {
    CurrencyUnit currencyUnit = CurrencyUnit.of(currencyCode);
    for (long minorUnits : minorUnitsSamples) {
      Money money = Money.ofMinorUnits(currencyUnit, minorUnits);
      assertEquals(money, Money.parse(currencyCode, money.canonicalDecimal()));
    }
  }
}
