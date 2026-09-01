package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for signed reporting-money arithmetic. */
class SignedMoneyTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  @Test
  void signedArithmeticPreservesDirectionCanonicalTextAndMagnitude() {
    SignedMoney taxCredit = SignedMoney.ofMinorUnits(EUR, -2_100L);

    assertTrue(taxCredit.isNegative());
    assertFalse(taxCredit.isPositive());
    assertFalse(taxCredit.isZero());
    assertEquals("-21.00", taxCredit.canonicalDecimal());
    assertEquals(Money.parse("EUR", "21.00"), taxCredit.magnitude());
    assertEquals(
        SignedMoney.ofMinorUnits(EUR, 1_050L),
        taxCredit.plus(SignedMoney.ofMinorUnits(EUR, 3_150L)));
    assertEquals(SignedMoney.ofMinorUnits(EUR, 2_100L), taxCredit.negated());
    assertEquals(
        SignedMoney.ofMinorUnits(EUR, -4_200L),
        taxCredit.minus(SignedMoney.ofMinorUnits(EUR, 2_100L)));
    assertEquals(
        SignedMoney.ofMinorUnits(EUR, 2_100L), SignedMoney.of(Money.parse("EUR", "21.00")));
  }

  @Test
  void signedArithmeticRejectsCrossCurrencyAndNonRepresentableNegation() {
    SignedMoney euro = SignedMoney.ofMinorUnits(EUR, 1L);

    assertThrows(
        IllegalArgumentException.class,
        () -> euro.plus(SignedMoney.ofMinorUnits(CurrencyUnit.of("USD"), 1L)));
    assertThrows(
        IllegalArgumentException.class,
        () -> euro.minus(SignedMoney.ofMinorUnits(CurrencyUnit.of("USD"), 1L)));
    assertThrows(
        IllegalArgumentException.class,
        () -> euro.compareTo(SignedMoney.ofMinorUnits(CurrencyUnit.of("USD"), 1L)));
    assertThrows(
        ArithmeticException.class, () -> SignedMoney.ofMinorUnits(EUR, Long.MIN_VALUE).negated());
    assertThrows(
        ArithmeticException.class, () -> SignedMoney.ofMinorUnits(EUR, Long.MIN_VALUE).magnitude());
  }

  @Test
  void signedZeroAndIdentityAccessorsDistinguishEverySignAndValue() {
    SignedMoney zero = SignedMoney.zero(EUR);
    SignedMoney euro = SignedMoney.ofMinorUnits(EUR, 100L);

    assertEquals(EUR, zero.currencyUnit());
    assertEquals(0L, zero.minorUnits());
    assertEquals(100L, euro.minorUnits());
    assertTrue(zero.isZero());
    assertFalse(zero.isPositive());
    assertFalse(zero.isNegative());
    assertTrue(euro.isPositive());
    assertEquals("0.00", zero.canonicalDecimal());
    assertTrue(euro.compareTo(zero) > 0);
    assertEquals(0, euro.compareTo(SignedMoney.ofMinorUnits(EUR, 100L)));
    assertEquals(euro, euro);
    assertEquals(euro, SignedMoney.ofMinorUnits(EUR, 100L));
    assertEquals(Objects.hash(EUR, 100L), euro.hashCode());
    assertNotEquals(euro, zero);
    assertNotEquals(euro, SignedMoney.ofMinorUnits(CurrencyUnit.of("USD"), 100L));
    assertNotEquals(euro, null);
    assertNotEquals(euro, "EUR 1.00");
    assertEquals(
        "SignedMoney[currencyUnit=CurrencyUnit[code=EUR, minorUnitScale=2], minorUnits=100, canonicalDecimal=1.00]",
        euro.toString());
  }
}
