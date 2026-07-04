package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.Money;
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
    InventoryBalanceBelowZero inventoryBalanceBelowZero =
        new InventoryBalanceBelowZero(
            new AccountCode("1400"),
            "inventoryRelief.amount",
            LocalDate.parse("2026-04-07"),
            BalanceSide.DEBIT,
            Money.parse("EUR", "10.00"),
            Money.parse("EUR", "50.00"),
            Money.parse("EUR", "40.00"));

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
        AccountStateViolationOwner.INVENTORY_BALANCE_BELOW_ZERO,
        AccountStateViolationOwner.require(inventoryBalanceBelowZero));

    assertEquals("unknown-account", AccountStateViolationOwner.code(unknownAccount));
    assertEquals("inactive-account", AccountStateViolationOwner.code(inactiveAccount));
    assertEquals("non-postable-account", AccountStateViolationOwner.code(nonPostableAccount));
    assertEquals(
        "inventory-balance-below-zero", AccountStateViolationOwner.code(inventoryBalanceBelowZero));
    assertEquals("lines[].accountCode", AccountStateViolationOwner.field(unknownAccount));
    assertEquals("lines[].accountCode", AccountStateViolationOwner.field(inactiveAccount));
    assertEquals("lines[].accountCode", AccountStateViolationOwner.field(nonPostableAccount));
    assertEquals(
        "inventoryRelief.amount", AccountStateViolationOwner.field(inventoryBalanceBelowZero));
    assertEquals("account-registry", AccountStateViolationOwner.category(unknownAccount));
    assertEquals("account-activation", AccountStateViolationOwner.category(inactiveAccount));
    assertEquals("account-node-kind", AccountStateViolationOwner.category(nonPostableAccount));
    assertEquals(
        "inventory-balance", AccountStateViolationOwner.category(inventoryBalanceBelowZero));
    assertEquals(
        "Declare the missing account before retrying the posting.",
        AccountStateViolationOwner.repair(unknownAccount));
    assertEquals(
        "Reactivate the account or replace it with an active posting account before retrying.",
        AccountStateViolationOwner.repair(inactiveAccount));
    assertEquals(
        "Replace the header account with a postable account before retrying.",
        AccountStateViolationOwner.repair(nonPostableAccount));
    assertEquals(
        "Reduce the requested inventory decrease, record the missing inventory acquisition first, or post a corrective inventory increase before retrying.",
        AccountStateViolationOwner.repair(inventoryBalanceBelowZero));
    assertEquals(new AccountCode("1000"), AccountStateViolationOwner.accountCode(unknownAccount));
    assertEquals(new AccountCode("2000"), AccountStateViolationOwner.accountCode(inactiveAccount));
    assertEquals(
        new AccountCode("3000"), AccountStateViolationOwner.accountCode(nonPostableAccount));
    assertEquals(
        new AccountCode("1400"), AccountStateViolationOwner.accountCode(inventoryBalanceBelowZero));
    assertNull(AccountStateViolationOwner.accountNodeKind(unknownAccount));
    assertNull(AccountStateViolationOwner.accountNodeKind(inactiveAccount));
    assertEquals("HEADER", AccountStateViolationOwner.accountNodeKind(nonPostableAccount));
    assertNull(AccountStateViolationOwner.accountNodeKind(inventoryBalanceBelowZero));
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
        "Request field 'inventoryRelief.amount' reduces inventory account '1400' on '2026-04-07' by EUR 50.00, but only EUR 10.00 is on hand; resulting balance would be EUR 40.00 credit.",
        AccountStateViolationOwner.message(inventoryBalanceBelowZero));

    AccountStateViolationDetail unknownDetail = PostingRejection.accountStateDetail(unknownAccount);
    AccountStateViolationDetail nonPostableDetail =
        PostingRejection.accountStateDetail(nonPostableAccount);
    AccountStateViolationDetail inventoryDetail =
        PostingRejection.accountStateDetail(inventoryBalanceBelowZero);

    assertEquals("unknown-account", unknownDetail.code());
    assertEquals("lines[].accountCode", unknownDetail.field());
    assertEquals("account-registry", unknownDetail.category());
    assertEquals("1000", unknownDetail.accountCode());
    assertNull(unknownDetail.accountNodeKind());
    assertEquals("non-postable-account", nonPostableDetail.code());
    assertEquals("3000", nonPostableDetail.accountCode());
    assertEquals("HEADER", nonPostableDetail.accountNodeKind());
    assertEquals("inventory-balance-below-zero", inventoryDetail.code());
    assertEquals("inventoryRelief.amount", inventoryDetail.field());
    assertEquals("1400", inventoryDetail.accountCode());
    assertNull(inventoryDetail.accountNodeKind());
  }

  @Test
  void canonicalOrdering_descriptors_andEnvelopeText_areStable() {
    List<PostingRejection.AccountStateViolation> canonicalOrder =
        AccountStateViolationOwner.inCanonicalOrder(
            List.of(
                new PostingRejection.NonPostableAccount(
                    new AccountCode("3000"), AccountNodeKind.HEADER),
                new PostingRejection.UnknownAccount(new AccountCode("2000")),
                new PostingRejection.InactiveAccount(new AccountCode("4000")),
                new InventoryBalanceBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.amount",
                    LocalDate.parse("2026-04-07"),
                    BalanceSide.DEBIT,
                    Money.parse("EUR", "10.00"),
                    Money.parse("EUR", "50.00"),
                    Money.parse("EUR", "40.00")),
                new PostingRejection.UnknownAccount(new AccountCode("1000"))));

    assertIterableEquals(
        List.of(
            new PostingRejection.UnknownAccount(new AccountCode("1000")),
            new PostingRejection.UnknownAccount(new AccountCode("2000")),
            new PostingRejection.InactiveAccount(new AccountCode("4000")),
            new PostingRejection.NonPostableAccount(
                new AccountCode("3000"), AccountNodeKind.HEADER),
            new InventoryBalanceBelowZero(
                new AccountCode("1400"),
                "inventoryRelief.amount",
                LocalDate.parse("2026-04-07"),
                BalanceSide.DEBIT,
                Money.parse("EUR", "10.00"),
                Money.parse("EUR", "50.00"),
                Money.parse("EUR", "40.00"))),
        canonicalOrder);

    List<ContractResponse.RejectionDescriptor> descriptors =
        AccountStateViolationOwner.descriptors();
    assertEquals(
        List.of(
            "unknown-account",
            "inactive-account",
            "non-postable-account",
            "inventory-balance-below-zero"),
        descriptors.stream().map(ContractResponse.RejectionDescriptor::code).toList());
    assertEquals(
        List.of("code", "field", "message", "category", "repair", "accountCode", "accountNodeKind"),
        descriptors.getFirst().detailFields().stream()
            .map(ContractResponse.FieldDescriptor::name)
            .toList());
    assertEquals(
        "Posting rejected with 5 account-state issues.",
        AccountStateViolationOwner.envelopeMessage(canonicalOrder));
    assertEquals(
        "Posting rejected with 1 account-state issue.",
        AccountStateViolationOwner.envelopeMessage(
            List.of(new PostingRejection.UnknownAccount(new AccountCode("1000")))));
  }

  @Test
  void inventoryBalanceMessage_namesExistingCreditBalanceTruthfully() {
    InventoryBalanceBelowZero inventoryBalanceBelowZero =
        new InventoryBalanceBelowZero(
            new AccountCode("1400"),
            "inventoryRelief.amount",
            LocalDate.parse("2026-04-08"),
            BalanceSide.CREDIT,
            Money.parse("EUR", "3.00"),
            Money.parse("EUR", "2.00"),
            Money.parse("EUR", "5.00"));

    assertEquals(
        "Request field 'inventoryRelief.amount' reduces inventory account '1400' on '2026-04-08' by EUR 2.00 while the account already carries a credit balance of EUR 3.00, deepening it to EUR 5.00 credit.",
        AccountStateViolationOwner.message(inventoryBalanceBelowZero));
  }
}
