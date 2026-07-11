package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused entry-semantics coverage for inventory admission. */
class InventoryAdmissionPolicyEntrySemanticsTest extends InventoryAdmissionPolicyTestSupport {
  @Test
  void resolve_rejectsSaleQuantityIncompatibleWithInventoryUnitOfMeasure() {
    RecordingValidationBook book = new RecordingValidationBook();
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("unit", 0);
    book.accounts.put(INVENTORY, inventoryAccount(INVENTORY, unitOfMeasure));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () -> POLICY.resolve(saleEntry(LocalDate.parse("2026-04-07"), "0.5"), book));

    assertEquals(
        new BookkeepingPostingRejection.EntrySemanticsViolations(
            List.of(
                InventoryEntrySemanticsViolations.inventoryQuantityIncompatibleWithUnitOfMeasure(
                    "inventoryRelief.quantity",
                    "0.5",
                    INVENTORY,
                    unitOfMeasure,
                    "Quantity must not contain fractional digits at scale 0."))),
        failure.rejection());
  }

  @Test
  void resolve_rejectsPurchaseQuantityIncompatibleWithInventoryUnitOfMeasure() {
    RecordingValidationBook book = new RecordingValidationBook();
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("unit", 0);
    book.accounts.put(INVENTORY, inventoryAccount(INVENTORY, unitOfMeasure));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () ->
                POLICY.resolve(
                    new BookkeepingEntry.PurchaseSettled(
                        LocalDate.parse("2026-04-07"),
                        INVENTORY,
                        new AccountCode("1000"),
                        new dev.erst.fingrind.contract.bookkeeping.QuantityText("0.5"),
                        new MonetaryAmount("EUR", "1000"),
                        null,
                        null,
                        null,
                        null),
                    book));

    assertEquals(
        new BookkeepingPostingRejection.EntrySemanticsViolations(
            List.of(
                InventoryEntrySemanticsViolations.inventoryQuantityIncompatibleWithUnitOfMeasure(
                    "quantity",
                    "0.5",
                    INVENTORY,
                    unitOfMeasure,
                    "Quantity must not contain fractional digits at scale 0."))),
        failure.rejection());
  }

  @Test
  void resolve_rejectsPurchaseWhoseQuantityAndUnitCostCannotComposeExactAcquisitionCost() {
    RecordingValidationBook book = new RecordingValidationBook();
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("kg", 2);
    book.accounts.put(INVENTORY_FRACTIONAL, inventoryAccount(INVENTORY_FRACTIONAL, unitOfMeasure));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () ->
                POLICY.resolve(
                    new BookkeepingEntry.PurchaseSettled(
                        LocalDate.parse("2026-04-07"),
                        INVENTORY_FRACTIONAL,
                        new AccountCode("1000"),
                        new dev.erst.fingrind.contract.bookkeeping.QuantityText("0.25"),
                        new MonetaryAmount("EUR", "2"),
                        null,
                        null,
                        null,
                        null),
                    book));

    assertEquals(
        new BookkeepingPostingRejection.EntrySemanticsViolations(
            List.of(
                InventoryEntrySemanticsViolations.inventoryAcquisitionCostNotExact(
                    "0.25", Money.parse("EUR", "0.02"), INVENTORY_FRACTIONAL, unitOfMeasure))),
        failure.rejection());
  }

  @Test
  void resolve_rejectsPurchaseThatWouldLeavePositivePoolBelowMinorUnitFloor() {
    RecordingValidationBook book = new RecordingValidationBook();
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("kg", 2);
    book.accounts.put(INVENTORY_FRACTIONAL, inventoryAccount(INVENTORY_FRACTIONAL, unitOfMeasure));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () ->
                POLICY.resolve(
                    new BookkeepingEntry.PurchaseSettled(
                        LocalDate.parse("2026-04-07"),
                        INVENTORY_FRACTIONAL,
                        new AccountCode("1000"),
                        new dev.erst.fingrind.contract.bookkeeping.QuantityText("0.25"),
                        new MonetaryAmount("EUR", "4"),
                        null,
                        null,
                        null,
                        null),
                    book));

    assertEquals(
        new BookkeepingPostingRejection.EntrySemanticsViolations(
            List.of(
                InventoryEntrySemanticsViolations.inventoryAcquisitionBreachesMinorUnitFloor(
                    "0.25",
                    Money.parse("EUR", "0.04"),
                    INVENTORY_FRACTIONAL,
                    unitOfMeasure,
                    25L,
                    Money.parse("EUR", "0.01")))),
        failure.rejection());
  }
}
