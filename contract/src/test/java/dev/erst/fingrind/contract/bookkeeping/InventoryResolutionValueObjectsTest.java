package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Coverage tests for resolved inventory value objects retained on typed purchase and write-down
 * flows.
 */
class InventoryResolutionValueObjectsTest {
  @Test
  void resolvedInventoryAcquisition_rejectsNonPositiveFacts() {
    assertDoesNotThrow(
        () ->
            new ResolvedInventoryAcquisition(
                Quantity.ofScaledUnits(0, 1),
                new MonetaryAmount("EUR", "100"),
                new MonetaryAmount("EUR", "100")));

    assertEquals(
        "quantityAcquired must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryAcquisition(
                        Quantity.ofScaledUnits(0, 0),
                        new MonetaryAmount("EUR", "100"),
                        new MonetaryAmount("EUR", "100")))
            .getMessage());
    assertEquals(
        "preTaxCost must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryAcquisition(
                        Quantity.ofScaledUnits(0, 1),
                        new MonetaryAmount("EUR", "0"),
                        new MonetaryAmount("EUR", "0")))
            .getMessage());
    assertEquals(
        "carryingCost must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryAcquisition(
                        Quantity.ofScaledUnits(0, 1),
                        new MonetaryAmount("EUR", "100"),
                        new MonetaryAmount("EUR", "0")))
            .getMessage());
    assertEquals(
        "preTaxCost and carryingCost must share one currency.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryAcquisition(
                        Quantity.ofScaledUnits(0, 1),
                        new MonetaryAmount("EUR", "100"),
                        new MonetaryAmount("USD", "100")))
            .getMessage());
    assertEquals(
        "carryingCost must not be less than preTaxCost.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryAcquisition(
                        Quantity.ofScaledUnits(0, 1),
                        new MonetaryAmount("EUR", "100"),
                        new MonetaryAmount("EUR", "99")))
            .getMessage());
  }

  @Test
  void resolvedInventoryDisposal_requiresPositiveSameCurrencyFacts() {
    assertDoesNotThrow(
        () ->
            new ResolvedInventoryDisposal(
                Money.parse("EUR", "10.00"),
                Quantity.ofScaledUnits(0, 1),
                Money.parse("EUR", "10.00")));

    assertEquals(
        "carryingCost must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryDisposal(
                        Money.parse("EUR", "0.00"),
                        Quantity.ofScaledUnits(0, 1),
                        Money.parse("EUR", "10.00")))
            .getMessage());
    assertEquals(
        "quantityDisposed must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryDisposal(
                        Money.parse("EUR", "10.00"),
                        Quantity.ofScaledUnits(0, 0),
                        Money.parse("EUR", "10.00")))
            .getMessage());
    assertEquals(
        "roundedMovingAverageUnitCostProjection must share carryingCost currency.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ResolvedInventoryDisposal(
                        Money.parse("EUR", "10.00"),
                        Quantity.ofScaledUnits(0, 1),
                        Money.parse("USD", "10.00")))
            .getMessage());
  }

  @Test
  void inventoryWriteDownExceedsCarryingCost_rejectsNonPositiveCostFacts() {
    assertDoesNotThrow(
        () ->
            new InventoryWriteDownExceedsCarryingCost(
                new AccountCode("inventory"),
                "inventoryWriteDown.amount",
                LocalDate.parse("2026-07-06"),
                Money.parse("EUR", "10.00"),
                Money.parse("EUR", "7.00"),
                Money.parse("EUR", "3.00")));

    assertEquals(
        "requestedCostDecrease must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new InventoryWriteDownExceedsCarryingCost(
                        new AccountCode("inventory"),
                        "inventoryWriteDown.amount",
                        LocalDate.parse("2026-07-06"),
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "0.00"),
                        Money.parse("EUR", "3.00")))
            .getMessage());
    assertEquals(
        "resultingCostShortfall must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new InventoryWriteDownExceedsCarryingCost(
                        new AccountCode("inventory"),
                        "inventoryWriteDown.amount",
                        LocalDate.parse("2026-07-06"),
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "7.00"),
                        Money.parse("EUR", "0.00")))
            .getMessage());
  }
}
