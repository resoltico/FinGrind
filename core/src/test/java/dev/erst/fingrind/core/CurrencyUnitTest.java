package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CurrencyUnit}. */
class CurrencyUnitTest {
  @Test
  void of_acceptsSupportedIsoCurrencyUnits() {
    CurrencyUnit eur = CurrencyUnit.of("EUR");
    CurrencyUnit jpy = CurrencyUnit.of("JPY");
    CurrencyUnit bhd = CurrencyUnit.of("BHD");

    assertEquals("EUR", eur.code());
    assertEquals(2, eur.minorUnitScale());
    assertEquals("JPY", jpy.code());
    assertEquals(0, jpy.minorUnitScale());
    assertEquals("BHD", bhd.code());
    assertEquals(3, bhd.minorUnitScale());
  }

  @Test
  void of_rejectsMalformedUnsupportedOrNonPostedCurrencyUnits() {
    assertEquals(
        "Currency unit code must not contain leading or trailing space.",
        assertThrows(IllegalArgumentException.class, () -> CurrencyUnit.of(" EUR ")).getMessage());
    assertEquals(
        "Currency unit code must be one canonical three-letter uppercase ISO 4217 code.",
        assertThrows(IllegalArgumentException.class, () -> CurrencyUnit.of("eur")).getMessage());
    assertEquals(
        "Currency unit code must be one canonical three-letter uppercase ISO 4217 code.",
        assertThrows(IllegalArgumentException.class, () -> CurrencyUnit.of("EU")).getMessage());
    assertEquals(
        "Unsupported currency unit code: ZZZ.",
        assertThrows(IllegalArgumentException.class, () -> CurrencyUnit.of("ZZZ")).getMessage());
    assertEquals(
        "Unsupported currency unit code: XAU.",
        assertThrows(IllegalArgumentException.class, () -> CurrencyUnit.of("XAU")).getMessage());
  }

  @Test
  void pinnedRegistry_isSortedUniqueAndBoundedByTheCoreScaleContract() {
    Map<String, Integer> registrySnapshot = CurrencyUnitRegistry.snapshot();

    assertTrue(registrySnapshot.size() > 200);
    assertEquals(
        registrySnapshot.keySet().stream().sorted().toList(),
        registrySnapshot.keySet().stream().toList());
    assertTrue(
        registrySnapshot.values().stream()
            .allMatch(scale -> scale >= 0 && scale <= CurrencyUnit.maxSupportedMinorUnitScale()));
  }

  @Test
  void equalityHashCodeAndToStringReflectCodeAndMinorUnitScale() {
    CurrencyUnit eur = CurrencyUnit.of("EUR");
    CurrencyUnit same = CurrencyUnit.of("EUR");

    assertEquals(eur, eur);
    assertEquals(eur, same);
    assertEquals(eur.hashCode(), same.hashCode());
    assertNotEquals(eur, CurrencyUnit.of("USD"));
    assertNotEquals(eur, "EUR");
    assertEquals("CurrencyUnit[code=EUR, minorUnitScale=2]", eur.toString());
  }
}
