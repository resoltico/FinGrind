package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the stable wire vocabulary for cash-flow section and asset classifications. */
class CashFlowVocabularyTest {
  @Test
  void cashFlowAssetClassification_roundTripsStableWireValues() {
    assertEquals(
        List.of("CASH_AND_CASH_EQUIVALENT", "NON_CASH"), CashFlowAssetClassification.wireValues());
    assertTrue(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT.cashAndCashEquivalent());
    assertFalse(CashFlowAssetClassification.NON_CASH.cashAndCashEquivalent());
    for (CashFlowAssetClassification classification : CashFlowAssetClassification.values()) {
      assertEquals(
          classification, CashFlowAssetClassification.fromWireValue(classification.wireValue()));
    }
  }

  @Test
  void cashFlowAssetClassification_rejectsNullAndUnknownValues() {
    assertThrows(
        NullPointerException.class, () -> CashFlowAssetClassification.fromWireValue(nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () -> CashFlowAssetClassification.fromWireValue("PETTY_CASH"));
  }

  @Test
  void cashFlowSectionKind_roundTripsStableWireValues() {
    assertEquals(List.of("OPERATING", "INVESTING", "FINANCING"), CashFlowSectionKind.wireValues());
    for (CashFlowSectionKind sectionKind : CashFlowSectionKind.values()) {
      assertEquals(sectionKind, CashFlowSectionKind.fromWireValue(sectionKind.wireValue()));
    }
  }

  @Test
  void cashFlowSectionKind_rejectsNullAndUnknownValues() {
    assertThrows(NullPointerException.class, () -> CashFlowSectionKind.fromWireValue(nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> CashFlowSectionKind.fromWireValue("TREASURY"));
  }
}
