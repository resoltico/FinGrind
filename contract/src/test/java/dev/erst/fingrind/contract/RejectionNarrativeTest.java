package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RejectionNarrative}. */
class RejectionNarrativeTest {
  @Test
  void administrationMessagesAndFactsCoverEveryRejection() {
    assertEquals(
        "The selected book is already initialized.",
        RejectionNarrative.message(new BookAdministrationRejection.BookAlreadyInitialized()));
    assertTrue(
        RejectionNarrative.message(new BookAdministrationRejection.BookNotInitialized())
            .contains("open-book"));
    assertTrue(
        RejectionNarrative.message(new BookAdministrationRejection.BookContainsSchema())
            .contains("schema objects"));

    BookAdministrationRejection.NormalBalanceConflict conflict =
        new BookAdministrationRejection.NormalBalanceConflict(
            new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT);

    assertTrue(RejectionNarrative.message(conflict).contains("1000"));
    assertEquals(
        List.of(
            LedgerFact.text("accountCode", "1000"),
            LedgerFact.text("existingNormalBalance", "DEBIT"),
            LedgerFact.text("requestedNormalBalance", "CREDIT")),
        RejectionNarrative.facts(conflict));
    assertEquals(
        List.of(),
        RejectionNarrative.facts(new BookAdministrationRejection.BookAlreadyInitialized()));
    assertEquals(
        List.of(), RejectionNarrative.facts(new BookAdministrationRejection.BookNotInitialized()));
    assertEquals(
        List.of(), RejectionNarrative.facts(new BookAdministrationRejection.BookContainsSchema()));
  }

  @Test
  void queryMessagesAndFactsCoverEveryRejection() {
    assertTrue(
        RejectionNarrative.message(new BookQueryRejection.BookNotInitialized())
            .contains("open-book"));

    BookQueryRejection.UnknownAccount unknownAccount =
        new BookQueryRejection.UnknownAccount(new AccountCode("9999"));
    BookQueryRejection.PostingNotFound postingNotFound =
        new BookQueryRejection.PostingNotFound(new PostingId("posting-1"));

    assertEquals(
        "Account '9999' is not declared in this book.", RejectionNarrative.message(unknownAccount));
    assertEquals(
        "Posting 'posting-1' does not exist in this book.",
        RejectionNarrative.message(postingNotFound));
    assertEquals(List.of(), RejectionNarrative.facts(new BookQueryRejection.BookNotInitialized()));
    assertEquals(
        List.of(LedgerFact.text("accountCode", "9999")), RejectionNarrative.facts(unknownAccount));
    assertEquals(
        List.of(LedgerFact.text("postingId", "posting-1")),
        RejectionNarrative.facts(postingNotFound));
  }

  @Test
  void postingMessagesAndFactsCoverEveryRejection() {
    PostingRejection.AccountStateViolations accountStateViolations =
        new PostingRejection.AccountStateViolations(
            List.of(
                new PostingRejection.UnknownAccount(new AccountCode("9999")),
                new PostingRejection.InactiveAccount(new AccountCode("1000"))));

    assertTrue(
        RejectionNarrative.message(new PostingRejection.BookNotInitialized())
            .contains("open-book"));
    assertTrue(RejectionNarrative.message(accountStateViolations).contains("Reported issues: 2"));
    assertTrue(
        RejectionNarrative.message(new PostingRejection.DuplicateIdempotencyKey())
            .contains("same idempotency key"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")))
            .contains("posting-1"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1")))
            .contains("full reversal"));
    assertTrue(
        RejectionNarrative.message(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1")))
            .contains("does not negate"));

    assertEquals(List.of(), RejectionNarrative.facts(new PostingRejection.BookNotInitialized()));
    assertEquals(
        List.of(
            LedgerFact.count("violationCount", 2),
            LedgerFact.group(
                "violation",
                List.of(
                    LedgerFact.text("code", "unknown-account"),
                    LedgerFact.text("accountCode", "9999"))),
            LedgerFact.group(
                "violation",
                List.of(
                    LedgerFact.text("code", "inactive-account"),
                    LedgerFact.text("accountCode", "1000")))),
        RejectionNarrative.facts(accountStateViolations));
    assertEquals(
        List.of(), RejectionNarrative.facts(new PostingRejection.DuplicateIdempotencyKey()));
    assertEquals(
        List.of(LedgerFact.text("priorPostingId", "posting-1")),
        RejectionNarrative.facts(
            new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))));
    assertEquals(
        List.of(LedgerFact.text("priorPostingId", "posting-1")),
        RejectionNarrative.facts(
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))));
    assertEquals(
        List.of(LedgerFact.text("priorPostingId", "posting-1")),
        RejectionNarrative.facts(
            new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1"))));
  }

  @Test
  void nullRejectionsAreRejected() {
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.message((BookAdministrationRejection) null));
    assertThrows(
        NullPointerException.class, () -> RejectionNarrative.message((BookQueryRejection) null));
    assertThrows(
        NullPointerException.class, () -> RejectionNarrative.message((PostingRejection) null));
    assertThrows(
        NullPointerException.class,
        () -> RejectionNarrative.facts((BookAdministrationRejection) null));
    assertThrows(
        NullPointerException.class, () -> RejectionNarrative.facts((BookQueryRejection) null));
    assertThrows(
        NullPointerException.class, () -> RejectionNarrative.facts((PostingRejection) null));
  }
}
