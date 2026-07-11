package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AccountStateViolationOwner}. */
class AccountStateViolationOwnerTest {
  @Test
  void ownerMetadata_andDetailExtraction_coverEveryViolationShape() {
    PostingRejection.UnknownAccount unknownAccount =
        new PostingRejection.UnknownAccount(new AccountCode("1000"));
    PostingRejection.InactiveAccount inactiveAccount =
        new PostingRejection.InactiveAccount(new AccountCode("2000"));
    PostingRejection.NonPostableAccount nonPostableAccount =
        new PostingRejection.NonPostableAccount(new AccountCode("3000"), AccountNodeKind.HEADER);
    InventoryMovementPrecedesAccountHorizon horizonViolation =
        new InventoryMovementPrecedesAccountHorizon(
            new AccountCode("inventory"),
            "inventoryRelief.quantity",
            LocalDate.parse("2026-04-07"),
            LocalDate.parse("2026-04-08"));
    InventoryQuantityBelowZero quantityViolation =
        new InventoryQuantityBelowZero(
            new AccountCode("inventory"),
            "inventoryRelief.quantity",
            LocalDate.parse("2026-04-07"),
            Quantity.ofScaledUnits(0, 10),
            Quantity.ofScaledUnits(0, 15),
            Quantity.ofScaledUnits(0, 5));
    InventoryWriteDownExceedsCarryingCost carryingCostViolation =
        new InventoryWriteDownExceedsCarryingCost(
            new AccountCode("inventory"),
            "inventoryWriteDown.amount",
            LocalDate.parse("2026-04-07"),
            Money.parse("EUR", "10.00"),
            Money.parse("EUR", "15.00"),
            Money.parse("EUR", "5.00"));

    assertEquals(
        AccountStateViolationOwner.UNKNOWN_ACCOUNT,
        AccountStateViolationOwner.require(unknownAccount));
    assertEquals(
        AccountStateViolationOwner.INACTIVE_ACCOUNT,
        AccountStateViolationOwner.require(inactiveAccount));
    assertEquals(
        AccountStateViolationOwner.NON_POSTABLE_ACCOUNT,
        AccountStateViolationOwner.require(nonPostableAccount));
    assertEquals(
        AccountStateViolationOwner.INVENTORY_MOVEMENT_PRECEDES_ACCOUNT_HORIZON,
        AccountStateViolationOwner.require(horizonViolation));
    assertEquals(
        AccountStateViolationOwner.INVENTORY_QUANTITY_BELOW_ZERO,
        AccountStateViolationOwner.require(quantityViolation));
    assertEquals(
        AccountStateViolationOwner.INVENTORY_WRITE_DOWN_EXCEEDS_CARRYING_COST,
        AccountStateViolationOwner.require(carryingCostViolation));

    assertEquals("unknown-account", AccountStateViolationOwner.code(unknownAccount));
    assertEquals("inactive-account", AccountStateViolationOwner.code(inactiveAccount));
    assertEquals("non-postable-account", AccountStateViolationOwner.code(nonPostableAccount));
    assertEquals(
        "inventory-movement-precedes-account-horizon",
        AccountStateViolationOwner.code(horizonViolation));
    assertEquals(
        "inventory-quantity-below-zero", AccountStateViolationOwner.code(quantityViolation));
    assertEquals(
        "inventory-write-down-exceeds-carrying-cost",
        AccountStateViolationOwner.code(carryingCostViolation));

    assertEquals("lines[].accountCode", AccountStateViolationOwner.field(unknownAccount));
    assertEquals("lines[].accountCode", AccountStateViolationOwner.field(inactiveAccount));
    assertEquals("lines[].accountCode", AccountStateViolationOwner.field(nonPostableAccount));
    assertEquals("inventoryRelief.quantity", AccountStateViolationOwner.field(horizonViolation));
    assertEquals("inventoryRelief.quantity", AccountStateViolationOwner.field(quantityViolation));
    assertEquals(
        "inventoryWriteDown.amount", AccountStateViolationOwner.field(carryingCostViolation));

    assertEquals(new AccountCode("1000"), AccountStateViolationOwner.accountCode(unknownAccount));
    assertEquals(new AccountCode("2000"), AccountStateViolationOwner.accountCode(inactiveAccount));
    assertEquals(
        new AccountCode("3000"), AccountStateViolationOwner.accountCode(nonPostableAccount));
    assertEquals(
        new AccountCode("inventory"), AccountStateViolationOwner.accountCode(horizonViolation));
    assertEquals(
        new AccountCode("inventory"), AccountStateViolationOwner.accountCode(quantityViolation));
    assertEquals(
        new AccountCode("inventory"),
        AccountStateViolationOwner.accountCode(carryingCostViolation));

    assertEquals("account-registry", AccountStateViolationOwner.category(unknownAccount));
    assertEquals("inventory-horizon", AccountStateViolationOwner.category(horizonViolation));
    assertEquals("inventory-quantity", AccountStateViolationOwner.category(quantityViolation));
    assertEquals(
        "inventory-carrying-cost", AccountStateViolationOwner.category(carryingCostViolation));

    assertEquals(
        "Journal line references undeclared account '1000'.",
        AccountStateViolationOwner.message(unknownAccount));
    assertEquals(
        "Journal line references inactive account '2000'.",
        AccountStateViolationOwner.message(inactiveAccount));
    assertEquals(
        "Journal line references header account '3000', declared as 'HEADER', which cannot accept direct postings.",
        AccountStateViolationOwner.message(nonPostableAccount));
    assertEquals(
        "Request field 'inventoryRelief.quantity' would record an inventory movement on '2026-04-07' for account 'inventory', but this account already has durable inventory history through '2026-04-08'.",
        AccountStateViolationOwner.message(horizonViolation));
    assertEquals(
        "Request field 'inventoryRelief.quantity' reduces inventory account 'inventory' on '2026-04-07' by 15 while only 10 is on hand; shortfall would be 5.",
        AccountStateViolationOwner.message(quantityViolation));
    assertEquals(
        "Request field 'inventoryWriteDown.amount' reduces inventory account 'inventory' on '2026-04-07' by EUR 15.00 while only EUR 10.00 of carrying cost is on hand; shortfall would be EUR 5.00.",
        AccountStateViolationOwner.message(carryingCostViolation));

    assertNull(AccountStateViolationOwner.accountNodeKind(unknownAccount));
    assertNull(AccountStateViolationOwner.accountNodeKind(inactiveAccount));
    assertEquals("HEADER", AccountStateViolationOwner.accountNodeKind(nonPostableAccount));
    assertNull(AccountStateViolationOwner.accountNodeKind(horizonViolation));
    assertNull(AccountStateViolationOwner.accountNodeKind(quantityViolation));
    assertNull(AccountStateViolationOwner.accountNodeKind(carryingCostViolation));

    AccountStateViolationDetail detail = PostingRejection.accountStateDetail(quantityViolation);
    assertEquals("inventory-quantity-below-zero", detail.code());
    assertEquals("inventoryRelief.quantity", detail.field());
    assertEquals("inventory-quantity", detail.category());
    assertEquals("inventory", detail.accountCode());
    assertNull(detail.accountNodeKind());
  }

  @Test
  void canonicalOrdering_descriptors_andEnvelopeText_areStable() {
    List<PostingRejection.AccountStateViolation> canonicalOrder =
        AccountStateViolationOwner.inCanonicalOrder(
            List.of(
                new InventoryQuantityBelowZero(
                    new AccountCode("inventory-b"),
                    "inventoryRelief.quantity",
                    LocalDate.parse("2026-04-07"),
                    Quantity.ofScaledUnits(0, 1),
                    Quantity.ofScaledUnits(0, 2),
                    Quantity.ofScaledUnits(0, 1)),
                new PostingRejection.UnknownAccount(new AccountCode("1000")),
                new PostingRejection.UnknownAccount(new AccountCode("2000")),
                new PostingRejection.InactiveAccount(new AccountCode("4000")),
                new PostingRejection.NonPostableAccount(
                    new AccountCode("3000"), AccountNodeKind.HEADER),
                new InventoryMovementPrecedesAccountHorizon(
                    new AccountCode("inventory-a"),
                    "inventoryRelief.quantity",
                    LocalDate.parse("2026-04-07"),
                    LocalDate.parse("2026-04-08"))));

    assertIterableEquals(
        List.of(
            new PostingRejection.UnknownAccount(new AccountCode("1000")),
            new PostingRejection.UnknownAccount(new AccountCode("2000")),
            new PostingRejection.InactiveAccount(new AccountCode("4000")),
            new PostingRejection.NonPostableAccount(
                new AccountCode("3000"), AccountNodeKind.HEADER),
            new InventoryMovementPrecedesAccountHorizon(
                new AccountCode("inventory-a"),
                "inventoryRelief.quantity",
                LocalDate.parse("2026-04-07"),
                LocalDate.parse("2026-04-08")),
            new InventoryQuantityBelowZero(
                new AccountCode("inventory-b"),
                "inventoryRelief.quantity",
                LocalDate.parse("2026-04-07"),
                Quantity.ofScaledUnits(0, 1),
                Quantity.ofScaledUnits(0, 2),
                Quantity.ofScaledUnits(0, 1))),
        canonicalOrder);

    List<ContractResponse.RejectionDescriptor> descriptors =
        AccountStateViolationOwner.descriptors();
    assertEquals(
        List.of(
            "unknown-account",
            "inactive-account",
            "non-postable-account",
            "inventory-movement-precedes-account-horizon",
            "inventory-quantity-below-zero",
            "inventory-write-down-exceeds-carrying-cost"),
        descriptors.stream().map(ContractResponse.RejectionDescriptor::code).toList());
    assertEquals(
        List.of("code", "field", "message", "category", "repair", "accountCode", "accountNodeKind"),
        descriptors.getFirst().detailFields().stream()
            .map(ContractResponse.FieldDescriptor::name)
            .toList());
    assertEquals(
        "Posting rejected with 6 account-state issues.",
        AccountStateViolationOwner.envelopeMessage(canonicalOrder));
    assertEquals(
        "Posting rejected with 1 account-state issue.",
        AccountStateViolationOwner.envelopeMessage(
            List.of(new PostingRejection.UnknownAccount(new AccountCode("1000")))));
  }
}
