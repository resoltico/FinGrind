package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.InventoryBalanceBelowZero;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for grouped workflow-failure mapping branches. */
class LedgerPlanWorkflowFailureMapperTest {
  @Test
  void postingFailure_handlesBothEmptyFactPostingRejections() {
    BookWorkflowFailure bookNotInitialized =
        LedgerPlanWorkflowFailureMapper.postingFailure(new PostingRejection.BookNotInitialized());
    BookWorkflowFailure duplicateIdempotencyKey =
        LedgerPlanWorkflowFailureMapper.postingFailure(
            new PostingRejection.IdempotencyKeyConflict());

    assertEquals("posting-book-not-initialized", bookNotInitialized.code());
    assertEquals(java.util.List.of(), bookNotInitialized.facts());
    assertEquals("idempotency-key-conflict", duplicateIdempotencyKey.code());
    assertEquals(java.util.List.of(), duplicateIdempotencyKey.facts());
  }

  @Test
  void postingFailure_publishesPriorPostingFactsForTerminalReversalTargets() {
    BookWorkflowFailure reversalTargetIsReversal =
        LedgerPlanWorkflowFailureMapper.postingFailure(
            new ReversalTargetIsReversal(new PostingId("posting-9")));

    assertEquals("reversal-target-is-reversal", reversalTargetIsReversal.code());
    assertEquals(
        java.util.List.of(BookWorkflowFact.text("priorPostingId", "posting-9")),
        reversalTargetIsReversal.facts());
  }

  @Test
  void postingFailure_publishesUnknownAndInventoryAccountStateViolationFacts() {
    BookWorkflowFailure rejection =
        LedgerPlanWorkflowFailureMapper.postingFailure(
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.UnknownAccount(new AccountCode("1000")),
                    new PostingRejection.NonPostableAccount(
                        new AccountCode("3000"), AccountNodeKind.HEADER),
                    new InventoryBalanceBelowZero(
                        new AccountCode("1400"),
                        "inventoryRelief.amount",
                        LocalDate.parse("2026-04-07"),
                        BalanceSide.CREDIT,
                        Money.parse("EUR", "10.00"),
                        Money.parse("EUR", "50.00"),
                        Money.parse("EUR", "60.00")))));

    assertEquals("account-state-violations", rejection.code());
    assertEquals(BookWorkflowFact.count("violationCount", 3), rejection.facts().getFirst());
    assertEquals(
        BookWorkflowFact.group(
            "violation",
            List.of(
                BookWorkflowFact.text("code", "unknown-account"),
                BookWorkflowFact.text("field", "lines[].accountCode"),
                BookWorkflowFact.text(
                    "message", "Journal line references undeclared account '1000'."),
                BookWorkflowFact.text("category", "account-registry"),
                BookWorkflowFact.text(
                    "repair", "Declare the missing account before retrying the posting."),
                BookWorkflowFact.text("accountCode", "1000"))),
        rejection.facts().get(1));
    assertEquals(
        BookWorkflowFact.group(
            "violation",
            List.of(
                BookWorkflowFact.text("code", "inventory-balance-below-zero"),
                BookWorkflowFact.text("field", "inventoryRelief.amount"),
                BookWorkflowFact.text(
                    "message",
                    "Request field 'inventoryRelief.amount' reduces inventory account '1400' on '2026-04-07' by EUR 50.00 while the account already carries a credit balance of EUR 10.00, deepening it to EUR 60.00 credit."),
                BookWorkflowFact.text("category", "inventory-balance"),
                BookWorkflowFact.text(
                    "repair",
                    "Reduce the requested inventory decrease, record the missing inventory acquisition first, or post a corrective inventory increase before retrying."),
                BookWorkflowFact.text("accountCode", "1400"),
                BookWorkflowFact.text("effectiveDate", "2026-04-07"),
                BookWorkflowFact.text("currentBalanceSide", "CREDIT"),
                BookWorkflowFact.money(
                    "currentNetAmount", MonetaryAmount.of(Money.parse("EUR", "10.00"))),
                BookWorkflowFact.money(
                    "requestedDecreaseAmount", MonetaryAmount.of(Money.parse("EUR", "50.00"))),
                BookWorkflowFact.money(
                    "resultingCreditBalance", MonetaryAmount.of(Money.parse("EUR", "60.00"))))),
        rejection.facts().get(3));
  }
}
