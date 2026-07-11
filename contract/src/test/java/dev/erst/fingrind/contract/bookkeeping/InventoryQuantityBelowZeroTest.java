package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InventoryQuantityBelowZero}. */
class InventoryQuantityBelowZeroTest {
  @Test
  void acceptsPositiveRequestedDecreaseAndShortfall() {
    InventoryQuantityBelowZero violation =
        assertDoesNotThrow(
            () ->
                new InventoryQuantityBelowZero(
                    new AccountCode("inventory"),
                    "inventoryRelief.quantity",
                    LocalDate.parse("2026-04-07"),
                    Quantity.ofScaledUnits(0, 10),
                    Quantity.ofScaledUnits(0, 15),
                    Quantity.ofScaledUnits(0, 5)));

    assertEquals("inventory", violation.accountCode().value());
    assertEquals("10", violation.quantityOnHand().canonicalDecimal());
  }

  @Test
  void rejectsNonPositiveRequestedDecrease() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryQuantityBelowZero(
                    new AccountCode("inventory"),
                    "inventoryRelief.quantity",
                    LocalDate.parse("2026-04-07"),
                    Quantity.ofScaledUnits(0, 10),
                    Quantity.zero(0),
                    Quantity.ofScaledUnits(0, 1)));

    assertEquals("requestedDecreaseQuantity must be positive.", error.getMessage());
  }

  @Test
  void rejectsNonPositiveShortfall() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryQuantityBelowZero(
                    new AccountCode("inventory"),
                    "inventoryRelief.quantity",
                    LocalDate.parse("2026-04-07"),
                    Quantity.ofScaledUnits(0, 10),
                    Quantity.ofScaledUnits(0, 15),
                    Quantity.zero(0)));

    assertEquals("resultingShortfallQuantity must be positive.", error.getMessage());
  }
}
