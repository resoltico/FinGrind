package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Direct coverage for the opening-only inventory state establishment workflow. */
class InventoryOpeningPositionResolverTest extends InventoryAdmissionPolicyTestSupport {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");

  @Test
  void resolve_establishesExactOpeningInventoryState() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());

    InventoryPostingResolution resolution =
        InventoryOpeningPositionResolver.resolve(openingInventory("1", "1000"), book);

    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                EFFECTIVE_DATE,
                dev.erst.fingrind.core.InventoryMovementKind.OPENING,
                1L,
                1000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 1), Money.parse("EUR", "10.00"), EFFECTIVE_DATE)),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_rejectsUndeclaredOpeningInventoryAccount() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                InventoryOpeningPositionResolver.resolve(
                    openingInventory("1", "1000"), new RecordingValidationBook()));

    assertEquals(
        "Opening inventory resolution requires declared account 1400.", failure.getMessage());
  }

  @Test
  void resolve_rejectsOpeningAfterTheInventoryAccountAlreadyHasMovement() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(Quantity.ofScaledUnits(0, 1), Money.parse("EUR", "10.00"), EFFECTIVE_DATE));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () -> POLICY.resolve(openingInventory("1", "1000"), book));

    assertEquals(
        new BookkeepingPostingRejection.EntrySemanticsViolations(
            List.of(
                BookkeepingEntryModeSemanticsViolations.inventoryOpeningMustBeFirstMovement(
                    INVENTORY))),
        failure.rejection());
  }

  @Test
  void resolve_rejectsOpeningInventoryThatBreaksTheZeroPoolInvariant() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () -> POLICY.resolve(openingInventory("0", "1000"), book));

    assertEquals(
        new BookkeepingPostingRejection.EntrySemanticsViolations(
            List.of(
                BookkeepingEntryModeSemanticsViolations.inventoryOpeningCarryingCostInvalid(
                    INVENTORY))),
        failure.rejection());
  }

  private static BookkeepingEntry.OpeningPosition openingInventory(
      String quantity, String amountMinorUnits) {
    return new BookkeepingEntry.OpeningPosition(
        EFFECTIVE_DATE,
        List.of(
            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                INVENTORY,
                JournalLine.EntrySide.DEBIT,
                new MonetaryAmount("EUR", amountMinorUnits),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText(quantity))));
  }
}
