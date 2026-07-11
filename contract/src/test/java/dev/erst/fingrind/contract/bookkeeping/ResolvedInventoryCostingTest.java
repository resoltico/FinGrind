package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import org.junit.jupiter.api.Test;

/** Coverage tests for executor-owned resolved inventory-disposal facts. */
class ResolvedInventoryCostingTest {
  @Test
  void rejectsNonPositiveResolvedInventoryCostingFacts() {
    assertDoesNotThrow(
        () ->
            new ResolvedInventoryCosting(
                Money.parse("EUR", "1.00"),
                Quantity.ofScaledUnits(0, 1),
                Money.parse("EUR", "1.00")));

    assertEquals(
        "costOfSales must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryCosting(
                        Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
                        Quantity.ofScaledUnits(0, 1),
                        Money.parse("EUR", "1.00")))
            .getMessage());
    assertEquals(
        "quantityRelieved must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryCosting(
                        Money.parse("EUR", "1.00"),
                        Quantity.ofScaledUnits(0, 0),
                        Money.parse("EUR", "1.00")))
            .getMessage());
    assertEquals(
        "roundedMovingAverageUnitCostProjection must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryCosting(
                        Money.parse("EUR", "1.00"),
                        Quantity.ofScaledUnits(0, 1),
                        Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR"))))
            .getMessage());
  }
}
