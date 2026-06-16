package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.Money;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MonetaryAmount}. */
class MonetaryAmountTest {
  @Test
  void canonicalMachineMoneyShapeRoundTripsThroughTheCoreMoneyModel() {
    MonetaryAmount amount = new MonetaryAmount("EUR", "1050");

    assertEquals("EUR", amount.currencyCode());
    assertEquals("1050", amount.minorUnits());
    assertEquals(Money.parse("EUR", "10.50"), amount.toMoney());
    assertEquals("10.50", amount.canonicalDecimal());
    assertEquals(amount, MonetaryAmount.of(Money.parse("EUR", "10.50")));
  }

  @Test
  void constructionRejectsBlankMalformedAndMismatchedMinorUnitShapes() {
    assertEquals(
        "minorUnits must not be blank.",
        assertThrows(IllegalArgumentException.class, () -> new MonetaryAmount("EUR", ""))
            .getMessage());
    assertEquals(
        "minorUnits must contain ASCII decimal digits only after removing signs and separators.",
        assertThrows(IllegalArgumentException.class, () -> new MonetaryAmount("EUR", "10a"))
            .getMessage());
    assertEquals(
        "minorUnits must contain ASCII decimal digits only after removing signs and separators.",
        assertThrows(IllegalArgumentException.class, () -> new MonetaryAmount("EUR", "10/"))
            .getMessage());
    assertEquals(
        "minorUnits must not be negative.",
        assertThrows(IllegalArgumentException.class, () -> new MonetaryAmount("EUR", "-10"))
            .getMessage());
    assertEquals(
        "minorUnits must not include a plus sign.",
        assertThrows(IllegalArgumentException.class, () -> new MonetaryAmount("EUR", "+10"))
            .getMessage());
    assertEquals(
        "minorUnits must be one exact minor-unit integer and must not include a decimal point.",
        assertThrows(IllegalArgumentException.class, () -> new MonetaryAmount("EUR", "10.5"))
            .getMessage());
    assertEquals(
        "minorUnits must not contain redundant leading zeroes.",
        assertThrows(IllegalArgumentException.class, () -> new MonetaryAmount("EUR", "010"))
            .getMessage());
    assertEquals(
        "minorUnits is outside the supported exact money range.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new MonetaryAmount("EUR", "9223372036854775808"))
            .getMessage());
    assertEquals(
        "minorUnits is outside the supported exact money range.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new MonetaryAmount("EUR", "1".repeat(Money.maxMinorUnitsDigitCount() + 1)))
            .getMessage());
  }

  @Test
  void factoryRejectsNullAndEqualityUsesTheCanonicalTuple() {
    MonetaryAmount amount = new MonetaryAmount("JPY", "100");

    assertEquals(
        "money",
        assertThrows(NullPointerException.class, () -> MonetaryAmount.of(nullOf(Money.class)))
            .getMessage());
    assertNotEquals(amount, new MonetaryAmount("EUR", "100"));
    assertEquals("MonetaryAmount[currencyCode=JPY, minorUnits=100]", amount.toString());
  }
}
