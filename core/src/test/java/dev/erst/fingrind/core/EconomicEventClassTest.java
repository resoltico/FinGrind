package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers stable economic-event vocabulary and typed-singleton membership. */
class EconomicEventClassTest {
  @Test
  void typedSingleton_marksOnlyOperationalAndStructuralSingletons() {
    Set<EconomicEventClass> singletons =
        EnumSet.of(
            EconomicEventClass.SETTLED_SALE,
            EconomicEventClass.CREDIT_SALE,
            EconomicEventClass.SETTLED_PURCHASE,
            EconomicEventClass.CREDIT_PURCHASE,
            EconomicEventClass.INVENTORY_CAPITALIZATION,
            EconomicEventClass.INVENTORY_WRITE_DOWN,
            EconomicEventClass.INVENTORY_SHRINKAGE,
            EconomicEventClass.INVENTORY_COUNT_INCREASE,
            EconomicEventClass.SETTLED_EXPENSE,
            EconomicEventClass.CREDIT_EXPENSE,
            EconomicEventClass.AR_SETTLEMENT,
            EconomicEventClass.AP_SETTLEMENT,
            EconomicEventClass.OWNER_CONTRIBUTION,
            EconomicEventClass.OWNER_WITHDRAWAL,
            EconomicEventClass.OPENING,
            EconomicEventClass.REVERSAL);
    for (EconomicEventClass eventClass : EconomicEventClass.values()) {
      if (singletons.contains(eventClass)) {
        assertTrue(eventClass.typedSingleton(), eventClass.name());
      } else {
        assertFalse(eventClass.typedSingleton(), eventClass.name());
      }
    }
  }

  @Test
  void wireValues_roundTripInDeclarationOrder() {
    assertEquals(
        List.of(
            "SETTLED_SALE",
            "CREDIT_SALE",
            "SETTLED_PURCHASE",
            "CREDIT_PURCHASE",
            "INVENTORY_CAPITALIZATION",
            "INVENTORY_WRITE_DOWN",
            "INVENTORY_SHRINKAGE",
            "INVENTORY_COUNT_INCREASE",
            "SETTLED_EXPENSE",
            "CREDIT_EXPENSE",
            "AR_SETTLEMENT",
            "AP_SETTLEMENT",
            "OWNER_CONTRIBUTION",
            "OWNER_WITHDRAWAL",
            "OPENING",
            "REVERSAL",
            "COMPOUND_OPERATIONAL",
            "ADJUSTMENT"),
        EconomicEventClass.wireValues());
    for (EconomicEventClass eventClass : EconomicEventClass.values()) {
      assertEquals(
          eventClass, EconomicEventClass.fromWireValue(eventClass.wireValue()), eventClass.name());
    }

    IllegalArgumentException unsupported =
        assertThrows(
            IllegalArgumentException.class,
            () -> EconomicEventClass.fromWireValue("UNSUPPORTED_EVENT_CLASS"));
    assertEquals(
        "Unsupported economicEventClass: UNSUPPORTED_EVENT_CLASS", unsupported.getMessage());
  }
}
