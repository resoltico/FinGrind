package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import org.junit.jupiter.api.Test;

/** Coverage tests for inventory-resolution guards on caller-authored bookkeeping entries. */
class BookkeepingEntryInventoryValidationSupportTest {
  @Test
  void requireResolvedInventoryFacts_rejectMissingExecutorOwnedFacts() {
    ResolvedInventoryCosting resolvedInventoryCosting =
        new ResolvedInventoryCosting(
            Money.parse("EUR", "1.00"), Quantity.ofScaledUnits(0, 1), Money.parse("EUR", "1.00"));
    ResolvedInventoryAcquisition resolvedInventoryAcquisition =
        new ResolvedInventoryAcquisition(
            Quantity.ofScaledUnits(0, 1),
            new MonetaryAmount("EUR", "100"),
            new MonetaryAmount("EUR", "100"));
    ResolvedInventoryDisposal resolvedInventoryDisposal =
        new ResolvedInventoryDisposal(
            Money.parse("EUR", "1.00"), Quantity.ofScaledUnits(0, 1), Money.parse("EUR", "1.00"));
    assertSame(
        resolvedInventoryCosting,
        BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryCosting(
            resolvedInventoryCosting, "SALE_SETTLED"));
    assertSame(
        resolvedInventoryAcquisition,
        BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryAcquisition(
            resolvedInventoryAcquisition, "PURCHASE_SETTLED"));
    assertSame(
        resolvedInventoryDisposal,
        BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryDisposal(
            resolvedInventoryDisposal, "INVENTORY_SHRINKAGE"));
    assertEquals(
        "SALE_SETTLED inventory relief requires executor-owned inventory costing before journalEntry() can be derived.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryCosting(
                        null, "SALE_SETTLED"))
            .getMessage());
    assertEquals(
        "PURCHASE_SETTLED inventory acquisition requires executor-owned quantity resolution before journalEntry() can be derived.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryAcquisition(
                        null, "PURCHASE_SETTLED"))
            .getMessage());
    assertEquals(
        "INVENTORY_SHRINKAGE inventory shrinkage requires executor-owned carrying-cost resolution before journalEntry() can be derived.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryDisposal(
                        null, "INVENTORY_SHRINKAGE"))
            .getMessage());
  }
}
