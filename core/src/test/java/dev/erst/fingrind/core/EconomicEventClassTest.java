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
            EconomicEventClass.PREPAYMENT,
            EconomicEventClass.DEFERRED_REVENUE,
            EconomicEventClass.ACCRUED_EXPENSE,
            EconomicEventClass.ACCRUAL_CUTOFF_RECOGNITION,
            EconomicEventClass.ACCRUED_EXPENSE_SETTLEMENT,
            EconomicEventClass.LATVIAN_MONTHLY_PAYROLL,
            EconomicEventClass.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
            EconomicEventClass.LATVIAN_PAYROLL_STATE_REMITTANCE,
            EconomicEventClass.FIXED_ASSET_CAPITALIZATION,
            EconomicEventClass.FIXED_ASSET_DEPRECIATION,
            EconomicEventClass.FIXED_ASSET_DISPOSAL,
            EconomicEventClass.FINANCING_BORROWING,
            EconomicEventClass.FINANCING_PRINCIPAL_REPAYMENT,
            EconomicEventClass.FINANCING_INTEREST_ACCRUAL,
            EconomicEventClass.FINANCING_INTEREST_PAYMENT,
            EconomicEventClass.FOREIGN_CURRENCY_OBLIGATION,
            EconomicEventClass.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
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
            "PREPAYMENT",
            "DEFERRED_REVENUE",
            "ACCRUED_EXPENSE",
            "ACCRUAL_CUTOFF_RECOGNITION",
            "ACCRUED_EXPENSE_SETTLEMENT",
            "LATVIAN_MONTHLY_PAYROLL",
            "LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT",
            "LATVIAN_PAYROLL_STATE_REMITTANCE",
            "FIXED_ASSET_CAPITALIZATION",
            "FIXED_ASSET_DEPRECIATION",
            "FIXED_ASSET_DISPOSAL",
            "FINANCING_BORROWING",
            "FINANCING_PRINCIPAL_REPAYMENT",
            "FINANCING_INTEREST_ACCRUAL",
            "FINANCING_INTEREST_PAYMENT",
            "FOREIGN_CURRENCY_OBLIGATION",
            "REALIZED_FOREIGN_EXCHANGE_SETTLEMENT",
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
