package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for executor-owned inventory admission and exact costing resolution. */
class InventoryAdmissionPolicyTest extends InventoryAdmissionPolicyTestSupport {

  @Test
  void resolve_rejectsEffectiveDateBeforeInventoryAccountHorizon() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 1),
            Money.parse("EUR", "10.00"),
            LocalDate.parse("2026-04-08")));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () -> POLICY.resolve(saleEntry(LocalDate.parse("2026-04-07"), "1"), book));

    assertEquals(
        new BookkeepingPostingRejection.AccountStateViolations(
            List.of(
                new InventoryMovementPrecedesAccountHorizonViolation(
                    INVENTORY,
                    "inventoryRelief.quantity",
                    LocalDate.parse("2026-04-07"),
                    LocalDate.parse("2026-04-08")))),
        failure.rejection());
  }

  @Test
  void resolve_rejectsBackdatedPurchaseAgainstInventoryAccountHorizonWithQuantityField() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 1),
            Money.parse("EUR", "10.00"),
            LocalDate.parse("2026-04-08")));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () ->
                POLICY.resolve(
                    new BookkeepingEntry.PurchaseSettled(
                        LocalDate.parse("2026-04-07"),
                        INVENTORY,
                        new AccountCode("1000"),
                        new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                        new MonetaryAmount("EUR", "1000"),
                        null,
                        null,
                        null,
                        null),
                    book));

    assertEquals(
        new BookkeepingPostingRejection.AccountStateViolations(
            List.of(
                new InventoryMovementPrecedesAccountHorizonViolation(
                    INVENTORY,
                    "quantity",
                    LocalDate.parse("2026-04-07"),
                    LocalDate.parse("2026-04-08")))),
        failure.rejection());
  }

  @Test
  void resolve_rejectsInventoryDecreaseThatWouldDriveQuantityBelowZero() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 1),
            Money.parse("EUR", "10.00"),
            LocalDate.parse("2026-04-06")));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () -> POLICY.resolve(saleEntry(LocalDate.parse("2026-04-07"), "2"), book));

    assertEquals(
        new BookkeepingPostingRejection.AccountStateViolations(
            List.of(
                new InventoryQuantityBelowZeroViolation(
                    INVENTORY,
                    "inventoryRelief.quantity",
                    LocalDate.parse("2026-04-07"),
                    Quantity.ofScaledUnits(0, 1),
                    Quantity.ofScaledUnits(0, 2),
                    Quantity.ofScaledUnits(0, 1)))),
        failure.rejection());
  }

  @Test
  void resolve_saleSettledAttachesResolvedInventoryCostingMovementAndResultingState() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 5),
            Money.parse("EUR", "50.00"),
            LocalDate.parse("2026-04-06")));

    InventoryPostingResolution resolution =
        POLICY.resolve(saleEntry(LocalDate.parse("2026-04-07"), "2"), book);

    BookkeepingEntry.SaleSettled resolvedEntry =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, resolution.resolvedEntry());

    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryCosting(
            Money.parse("EUR", "20.00"), Quantity.ofScaledUnits(0, 2), Money.parse("EUR", "10.00")),
        resolvedEntry.resolvedInventoryCosting());
    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.DISPOSAL,
                -2L,
                -2000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 3),
                Money.parse("EUR", "30.00"),
                LocalDate.parse("2026-04-07"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_saleOnCreditAttachesResolvedInventoryCostingMovementAndResultingState() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 4),
            Money.parse("EUR", "40.00"),
            LocalDate.parse("2026-04-06")));

    InventoryPostingResolution resolution =
        POLICY.resolve(saleOnCreditEntry(LocalDate.parse("2026-04-07"), "1"), book);

    BookkeepingEntry.SaleOnCredit resolvedEntry =
        assertInstanceOf(BookkeepingEntry.SaleOnCredit.class, resolution.resolvedEntry());

    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryCosting(
            Money.parse("EUR", "10.00"), Quantity.ofScaledUnits(0, 1), Money.parse("EUR", "10.00")),
        resolvedEntry.resolvedInventoryCosting());
    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.DISPOSAL,
                -1L,
                -1000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 3),
                Money.parse("EUR", "30.00"),
                LocalDate.parse("2026-04-07"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_purchaseAttachesResolvedAcquisitionMovementAndResultingState() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());

    InventoryPostingResolution resolution =
        POLICY.resolve(
            new BookkeepingEntry.PurchaseSettled(
                LocalDate.parse("2026-04-07"),
                INVENTORY,
                new AccountCode("1000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("2"),
                new MonetaryAmount("EUR", "500"),
                null,
                null,
                null,
                null),
            book);

    BookkeepingEntry.PurchaseSettled resolvedEntry =
        assertInstanceOf(BookkeepingEntry.PurchaseSettled.class, resolution.resolvedEntry());

    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition(
            Quantity.ofScaledUnits(0, 2),
            new MonetaryAmount("EUR", "1000"),
            new MonetaryAmount("EUR", "1000")),
        resolvedEntry.resolvedInventoryAcquisition());
    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.ACQUISITION,
                2L,
                1000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 2),
                Money.parse("EUR", "10.00"),
                LocalDate.parse("2026-04-07"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_purchaseComparesForeignExchangeToExactResolvedAcquisitionCost() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    ForeignExchangeDetails matchingForeignExchange =
        foreignExchange(new MonetaryAmount("EUR", "1000"));

    InventoryPostingResolution resolution =
        POLICY.resolve(
            new BookkeepingEntry.PurchaseSettled(
                LocalDate.parse("2026-04-07"),
                INVENTORY,
                new AccountCode("1000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("2"),
                new MonetaryAmount("EUR", "500"),
                null,
                matchingForeignExchange,
                null,
                null),
            book);

    BookkeepingEntry.PurchaseSettled resolvedEntry =
        assertInstanceOf(BookkeepingEntry.PurchaseSettled.class, resolution.resolvedEntry());
    assertEquals(matchingForeignExchange, resolvedEntry.foreignExchangeDetails());
    assertEquals(
        new MonetaryAmount("EUR", "1000"),
        java.util.Objects.requireNonNull(resolvedEntry.resolvedInventoryAcquisition())
            .preTaxCost());

    InventoryAdmissionPolicy.InventoryAdmissionFailure mismatch =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () ->
                POLICY.resolve(
                    new BookkeepingEntry.PurchaseSettled(
                        LocalDate.parse("2026-04-07"),
                        INVENTORY,
                        new AccountCode("1000"),
                        new dev.erst.fingrind.contract.bookkeeping.QuantityText("2"),
                        new MonetaryAmount("EUR", "500"),
                        null,
                        foreignExchange(new MonetaryAmount("EUR", "500")),
                        null,
                        null),
                    book));

    assertEquals(
        new BookkeepingPostingRejection.EntrySemanticsViolations(
            List.of(
                InventoryEntrySemanticsViolations
                    .inventoryAcquisitionForeignExchangeFunctionalAmountMismatch(
                        "entryKind",
                        "PURCHASE_SETTLED",
                        new MonetaryAmount("EUR", "1000"),
                        new MonetaryAmount("EUR", "500")))),
        mismatch.rejection());
  }

  @Test
  void resolve_purchaseOnCreditAttachesResolvedAcquisitionMovementAndResultingState() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());

    InventoryPostingResolution resolution =
        POLICY.resolve(
            new BookkeepingEntry.PurchaseOnCredit(
                LocalDate.parse("2026-04-07"),
                INVENTORY,
                new AccountCode("2000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("3"),
                new MonetaryAmount("EUR", "250"),
                null,
                null,
                null,
                null),
            book);

    BookkeepingEntry.PurchaseOnCredit resolvedEntry =
        assertInstanceOf(BookkeepingEntry.PurchaseOnCredit.class, resolution.resolvedEntry());

    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition(
            Quantity.ofScaledUnits(0, 3),
            new MonetaryAmount("EUR", "750"),
            new MonetaryAmount("EUR", "750")),
        resolvedEntry.resolvedInventoryAcquisition());
    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.ACQUISITION,
                3L,
                750L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 3),
                Money.parse("EUR", "7.50"),
                LocalDate.parse("2026-04-07"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_rejectsCostOnlyCapitalizationBeforeInventoryHasQuantity() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () ->
                POLICY.resolve(
                    new dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants
                        .InventoryCapitalizationSettled(
                        LocalDate.parse("2026-04-07"),
                        INVENTORY,
                        new AccountCode("1000"),
                        new MonetaryAmount("EUR", "1000"),
                        null,
                        null,
                        null),
                    book));

    assertEquals(
        new BookkeepingPostingRejection.EntrySemanticsViolations(
            List.of(
                BookkeepingEntryModeSemanticsViolations
                    .inventoryCapitalizationRequiresQuantityOnHand(INVENTORY))),
        failure.rejection());
  }

  @Test
  void resolve_reversalRejectsWhenCompensatingMovementWouldOverdrawCarryingCost() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 2),
            Money.parse("EUR", "5.00"),
            LocalDate.parse("2026-04-07")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.ACQUISITION,
                1L,
                1000L)));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () ->
                POLICY.resolve(
                    reversalEntry(
                        LocalDate.parse("2026-04-08"),
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                        List.of(
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("1000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                Money.parse("EUR", "10.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                INVENTORY,
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "10.00")))),
                    book));

    assertEquals(
        new BookkeepingPostingRejection.AccountStateViolations(
            List.of(
                new InventoryWriteDownExceedsCarryingCostViolation(
                    INVENTORY,
                    "reversal.priorPostingId",
                    LocalDate.parse("2026-04-08"),
                    Money.parse("EUR", "5.00"),
                    Money.parse("EUR", "10.00"),
                    Money.parse("EUR", "5.00")))),
        failure.rejection());
  }

  @Test
  void resolve_rejectsBackdatedReversalAgainstInventoryAccountHorizonWithPriorPostingField() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 2),
            Money.parse("EUR", "20.00"),
            LocalDate.parse("2026-04-08")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-08"),
                InventoryMovementKind.DISPOSAL,
                -1L,
                -1000L)));

    InventoryAdmissionPolicy.InventoryAdmissionFailure failure =
        assertThrows(
            InventoryAdmissionPolicy.InventoryAdmissionFailure.class,
            () ->
                POLICY.resolve(
                    reversalEntry(
                        LocalDate.parse("2026-04-07"),
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                        List.of(
                            new dev.erst.fingrind.core.JournalLine(
                                INVENTORY,
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                Money.parse("EUR", "10.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("5000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "10.00")))),
                    book));

    assertEquals(
        new BookkeepingPostingRejection.AccountStateViolations(
            List.of(
                new InventoryMovementPrecedesAccountHorizonViolation(
                    INVENTORY,
                    "reversal.priorPostingId",
                    LocalDate.parse("2026-04-07"),
                    LocalDate.parse("2026-04-08")))),
        failure.rejection());
  }

  @Test
  void resolve_reversalRemovesExactAcquiredQuantityAndCostWhenPoolRemainsNonNegative() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 5),
            Money.parse("EUR", "50.00"),
            LocalDate.parse("2026-04-07")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.ACQUISITION,
                2L,
                2000L)));

    InventoryPostingResolution resolution =
        POLICY.resolve(
            reversalEntry(
                LocalDate.parse("2026-04-08"),
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("1000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "20.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        INVENTORY,
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "20.00")))),
            book);

    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-08"),
                InventoryMovementKind.REVERSAL_COMP,
                -2L,
                -2000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 3),
                Money.parse("EUR", "30.00"),
                LocalDate.parse("2026-04-08"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_reversalRemovesCapitalizedCostWithoutChangingQuantity() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 3),
            Money.parse("EUR", "35.00"),
            LocalDate.parse("2026-04-07")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.CAPITALIZATION,
                0L,
                500L)));

    InventoryPostingResolution resolution =
        POLICY.resolve(
            reversalEntry(
                LocalDate.parse("2026-04-08"),
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("5090"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "5.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        INVENTORY,
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "5.00")))),
            book);

    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-08"),
                InventoryMovementKind.REVERSAL_COMP,
                0L,
                -500L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 3),
                Money.parse("EUR", "30.00"),
                LocalDate.parse("2026-04-08"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_reversalRemovesCountIncreaseQuantityAndCostWhenPoolRemainsNonNegative() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 5),
            Money.parse("EUR", "50.00"),
            LocalDate.parse("2026-04-07")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.COUNT_INCREASE,
                2L,
                2000L)));

    InventoryPostingResolution resolution =
        POLICY.resolve(
            reversalEntry(
                LocalDate.parse("2026-04-08"),
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("5090"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "20.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        INVENTORY,
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "20.00")))),
            book);

    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-08"),
                InventoryMovementKind.REVERSAL_COMP,
                -2L,
                -2000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 3),
                Money.parse("EUR", "30.00"),
                LocalDate.parse("2026-04-08"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_reversalRemovesOpeningQuantityAndCostWhenPoolRemainsNonNegative() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 5),
            Money.parse("EUR", "50.00"),
            LocalDate.parse("2026-04-07")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.OPENING,
                2L,
                2000L)));

    InventoryPostingResolution resolution =
        POLICY.resolve(
            reversalEntry(
                LocalDate.parse("2026-04-08"),
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("3200"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "20.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        INVENTORY,
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "20.00")))),
            book);

    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-08"),
                InventoryMovementKind.REVERSAL_COMP,
                -2L,
                -2000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 3),
                Money.parse("EUR", "30.00"),
                LocalDate.parse("2026-04-08"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_reversalRestoresWriteDownCostWithoutChangingQuantity() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 3),
            Money.parse("EUR", "25.00"),
            LocalDate.parse("2026-04-07")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.WRITE_DOWN,
                0L,
                -500L)));

    InventoryPostingResolution resolution =
        POLICY.resolve(
            reversalEntry(
                LocalDate.parse("2026-04-08"),
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        INVENTORY,
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "5.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("5090"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "5.00")))),
            book);

    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-08"),
                InventoryMovementKind.REVERSAL_COMP,
                0L,
                500L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 3),
                Money.parse("EUR", "30.00"),
                LocalDate.parse("2026-04-08"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_reversalRestoresExactDisposedQuantityAndCost() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 3),
            Money.parse("EUR", "30.00"),
            LocalDate.parse("2026-04-07")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.DISPOSAL,
                -2L,
                -2000L)));

    InventoryPostingResolution resolution =
        POLICY.resolve(
            reversalEntry(
                LocalDate.parse("2026-04-08"),
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        INVENTORY,
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "20.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("5000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "20.00")))),
            book);

    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-08"),
                InventoryMovementKind.REVERSAL_COMP,
                2L,
                2000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 5),
                Money.parse("EUR", "50.00"),
                LocalDate.parse("2026-04-08"))),
        resolution.resultingInventoryStates());
  }

  @Test
  void resolve_reversalWithoutPriorInventoryMovementsReturnsNoInventoryEffects() {
    RecordingValidationBook book = new RecordingValidationBook();

    BookkeepingEntry.Reversal reversal =
        reversalEntry(
            LocalDate.parse("2026-04-08"),
            new PostingId("c3976320-f339-39d8-a06e-36c2e2b14bc3"),
            List.of(
                new dev.erst.fingrind.core.JournalLine(
                    new AccountCode("1000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "20.00")),
                new dev.erst.fingrind.core.JournalLine(
                    new AccountCode("5000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "20.00"))));

    InventoryPostingResolution resolution = POLICY.resolve(reversal, book);

    assertEquals(InventoryPostingResolution.withoutInventory(reversal), resolution);
  }

  @Test
  void resolve_reversalRestoresShrinkageQuantityAndCost() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.accounts.put(INVENTORY, inventoryAccount());
    book.states.put(
        INVENTORY,
        inventoryState(
            Quantity.ofScaledUnits(0, 3),
            Money.parse("EUR", "30.00"),
            LocalDate.parse("2026-04-07")));
    book.movementsByPostingId.put(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-07"),
                InventoryMovementKind.SHRINKAGE,
                -1L,
                -1000L)));

    InventoryPostingResolution resolution =
        POLICY.resolve(
            reversalEntry(
                LocalDate.parse("2026-04-08"),
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        INVENTORY,
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("5090"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            book);

    assertEquals(
        List.of(
            new InventoryMovementRecord(
                INVENTORY,
                LocalDate.parse("2026-04-08"),
                InventoryMovementKind.REVERSAL_COMP,
                1L,
                1000L)),
        resolution.inventoryMovements());
    assertEquals(
        Map.of(
            INVENTORY,
            inventoryState(
                Quantity.ofScaledUnits(0, 4),
                Money.parse("EUR", "40.00"),
                LocalDate.parse("2026-04-08"))),
        resolution.resultingInventoryStates());
  }

  private static ForeignExchangeDetails foreignExchange(MonetaryAmount functionalAmount) {
    long transactionMinorUnits = Math.multiplyExact(functionalAmount.toMoney().minorUnits(), 2L);
    MonetaryAmount transactionAmount =
        new MonetaryAmount("USD", Long.toString(transactionMinorUnits));
    return new ForeignExchangeDetails(
        transactionAmount,
        functionalAmount,
        new QuotedExchangeRate(
            transactionAmount, functionalAmount, LocalDate.parse("2026-04-06"), "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }
}
