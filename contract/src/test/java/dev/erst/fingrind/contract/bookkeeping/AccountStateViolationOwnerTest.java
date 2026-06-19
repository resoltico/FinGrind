package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
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

    assertEquals(
        AccountStateViolationOwner.UNKNOWN_ACCOUNT,
        AccountStateViolationOwner.require(unknownAccount));
    assertEquals(
        AccountStateViolationOwner.INACTIVE_ACCOUNT,
        AccountStateViolationOwner.require(inactiveAccount));
    assertEquals(
        AccountStateViolationOwner.NON_POSTABLE_ACCOUNT,
        AccountStateViolationOwner.require(nonPostableAccount));

    assertEquals("unknown-account", AccountStateViolationOwner.code(unknownAccount));
    assertEquals("inactive-account", AccountStateViolationOwner.code(inactiveAccount));
    assertEquals("non-postable-account", AccountStateViolationOwner.code(nonPostableAccount));
    assertEquals("lines[].accountCode", AccountStateViolationOwner.field(unknownAccount));
    assertEquals("account-registry", AccountStateViolationOwner.category(unknownAccount));
    assertEquals("account-activation", AccountStateViolationOwner.category(inactiveAccount));
    assertEquals("account-node-kind", AccountStateViolationOwner.category(nonPostableAccount));
    assertEquals(
        "Declare the missing account before retrying the posting.",
        AccountStateViolationOwner.repair(unknownAccount));
    assertEquals(
        "Reactivate the account or replace it with an active posting account before retrying.",
        AccountStateViolationOwner.repair(inactiveAccount));
    assertEquals(
        "Replace the header account with a postable account before retrying.",
        AccountStateViolationOwner.repair(nonPostableAccount));
    assertEquals(new AccountCode("1000"), AccountStateViolationOwner.accountCode(unknownAccount));
    assertEquals(new AccountCode("2000"), AccountStateViolationOwner.accountCode(inactiveAccount));
    assertEquals(
        new AccountCode("3000"), AccountStateViolationOwner.accountCode(nonPostableAccount));
    assertNull(AccountStateViolationOwner.accountNodeKind(unknownAccount));
    assertNull(AccountStateViolationOwner.accountNodeKind(inactiveAccount));
    assertEquals("HEADER", AccountStateViolationOwner.accountNodeKind(nonPostableAccount));
    assertEquals(
        "Journal line references undeclared account '1000'.",
        AccountStateViolationOwner.message(unknownAccount));
    assertEquals(
        "Journal line references inactive account '2000'.",
        AccountStateViolationOwner.message(inactiveAccount));
    assertEquals(
        "Journal line references header account '3000', declared as 'HEADER', which cannot accept direct postings.",
        AccountStateViolationOwner.message(nonPostableAccount));

    PostingRejection.AccountStateViolationDetail unknownDetail =
        PostingRejection.accountStateDetail(unknownAccount);
    PostingRejection.AccountStateViolationDetail nonPostableDetail =
        PostingRejection.accountStateDetail(nonPostableAccount);

    assertEquals("unknown-account", unknownDetail.code());
    assertEquals("lines[].accountCode", unknownDetail.field());
    assertEquals("account-registry", unknownDetail.category());
    assertEquals("1000", unknownDetail.accountCode());
    assertNull(unknownDetail.accountNodeKind());
    assertEquals("non-postable-account", nonPostableDetail.code());
    assertEquals("3000", nonPostableDetail.accountCode());
    assertEquals("HEADER", nonPostableDetail.accountNodeKind());
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
                new PostingRejection.UnknownAccount(new AccountCode("1000"))));

    assertIterableEquals(
        List.of(
            new PostingRejection.UnknownAccount(new AccountCode("1000")),
            new PostingRejection.UnknownAccount(new AccountCode("2000")),
            new PostingRejection.InactiveAccount(new AccountCode("4000")),
            new PostingRejection.NonPostableAccount(
                new AccountCode("3000"), AccountNodeKind.HEADER)),
        canonicalOrder);

    List<ContractResponse.RejectionDescriptor> descriptors =
        AccountStateViolationOwner.descriptors();
    assertEquals(
        List.of("unknown-account", "inactive-account", "non-postable-account"),
        descriptors.stream().map(ContractResponse.RejectionDescriptor::code).toList());
    assertEquals(
        List.of("code", "field", "message", "category", "repair", "accountCode", "accountNodeKind"),
        descriptors.getFirst().detailFields().stream()
            .map(ContractResponse.FieldDescriptor::name)
            .toList());
    assertEquals(
        "Posting rejected with 4 account-state issues.",
        AccountStateViolationOwner.envelopeMessage(canonicalOrder));
    assertEquals(
        "Posting rejected with 1 account-state issue.",
        AccountStateViolationOwner.envelopeMessage(
            List.of(new PostingRejection.UnknownAccount(new AccountCode("1000")))));
  }
}
