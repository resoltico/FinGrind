package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.InventoryQuantityBelowZero;
import dev.erst.fingrind.contract.bookkeeping.InventoryWriteDownExceedsCarryingCost;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxDefinitionViolation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
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
            new ReversalTargetIsReversal(new PostingId("7982b5de-2f28-355e-9911-9ca85b4f5a67")));

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
                    new InventoryQuantityBelowZero(
                        new AccountCode("1400"),
                        "inventoryRelief.quantity",
                        LocalDate.parse("2026-04-07"),
                        Quantity.ofScaledUnits(0, 1),
                        Quantity.ofScaledUnits(0, 5),
                        Quantity.ofScaledUnits(0, 4)))));

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
                BookWorkflowFact.text("code", "inventory-quantity-below-zero"),
                BookWorkflowFact.text("field", "inventoryRelief.quantity"),
                BookWorkflowFact.text(
                    "message",
                    "Request field 'inventoryRelief.quantity' reduces inventory account '1400' on '2026-04-07' by 5 while only 1 is on hand; shortfall would be 4."),
                BookWorkflowFact.text("category", "inventory-quantity"),
                BookWorkflowFact.text(
                    "repair",
                    "Reduce the requested inventory decrease, record the missing inventory acquisition first, or post a corrective inventory increase before retrying."),
                BookWorkflowFact.text("accountCode", "1400"),
                BookWorkflowFact.text("effectiveDate", "2026-04-07"),
                BookWorkflowFact.text("quantityOnHand", "1"),
                BookWorkflowFact.text("requestedDecreaseQuantity", "5"),
                BookWorkflowFact.text("resultingShortfallQuantity", "4"))),
        rejection.facts().get(3));
  }

  @Test
  void postingFailure_publishesInactiveHorizonAndWriteDownAccountStateFacts() {
    BookWorkflowFailure rejection =
        LedgerPlanWorkflowFailureMapper.postingFailure(
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.InactiveAccount(new AccountCode("2000")),
                    new dev.erst.fingrind.contract.bookkeeping
                        .InventoryMovementPrecedesAccountHorizon(
                        new AccountCode("1400"),
                        "inventoryRelief.quantity",
                        LocalDate.parse("2026-04-07"),
                        LocalDate.parse("2026-04-08")),
                    new InventoryWriteDownExceedsCarryingCost(
                        new AccountCode("1400"),
                        "reversal.priorPostingId",
                        LocalDate.parse("2026-04-09"),
                        Money.ofMinorUnits(CurrencyUnit.of("EUR"), 500L),
                        Money.ofMinorUnits(CurrencyUnit.of("EUR"), 900L),
                        Money.ofMinorUnits(CurrencyUnit.of("EUR"), 400L)))));

    assertEquals("account-state-violations", rejection.code());
    assertEquals(BookWorkflowFact.count("violationCount", 3), rejection.facts().getFirst());
    assertEquals(
        BookWorkflowFact.group(
            "violation",
            List.of(
                BookWorkflowFact.text("code", "inventory-movement-precedes-account-horizon"),
                BookWorkflowFact.text("field", "inventoryRelief.quantity"),
                BookWorkflowFact.text(
                    "message",
                    "Request field 'inventoryRelief.quantity' would record an inventory movement on '2026-04-07' for account '1400', but this account already has durable inventory history through '2026-04-08'."),
                BookWorkflowFact.text("category", "inventory-horizon"),
                BookWorkflowFact.text(
                    "repair",
                    "Retry with an effective date on or after the account horizon, or reverse later movements before restating earlier inventory history."),
                BookWorkflowFact.text("accountCode", "1400"),
                BookWorkflowFact.text("attemptedEffectiveDate", "2026-04-07"),
                BookWorkflowFact.text("accountHorizonEffectiveDate", "2026-04-08"))),
        rejection.facts().get(2));
    assertEquals(
        BookWorkflowFact.group(
            "violation",
            List.of(
                BookWorkflowFact.text("code", "inventory-write-down-exceeds-carrying-cost"),
                BookWorkflowFact.text("field", "reversal.priorPostingId"),
                BookWorkflowFact.text(
                    "message",
                    "Request field 'reversal.priorPostingId' reduces inventory account '1400' on '2026-04-09' by EUR 9.00 while only EUR 5.00 of carrying cost is on hand; shortfall would be EUR 4.00."),
                BookWorkflowFact.text("category", "inventory-carrying-cost"),
                BookWorkflowFact.text(
                    "repair",
                    "Reduce the requested inventory cost decrease, capitalize the missing cost first, or post a corrective inventory increase before retrying."),
                BookWorkflowFact.text("accountCode", "1400"),
                BookWorkflowFact.text("effectiveDate", "2026-04-09"),
                BookWorkflowFact.money(
                    "carryingCostOnHand",
                    new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "500")),
                BookWorkflowFact.money(
                    "requestedCostDecrease",
                    new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "900")),
                BookWorkflowFact.money(
                    "resultingCostShortfall",
                    new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "400")))),
        rejection.facts().get(3));
  }

  @Test
  void taxDeclarationFailure_preservesMissingBookAndDefinitionViolationFacts() {
    BookWorkflowFailure missingBook =
        LedgerPlanWorkflowFailureMapper.taxDeclarationFailure(
            new TaxDeclarationRejection.BookNotInitialized());
    BookWorkflowFailure definitionViolations =
        LedgerPlanWorkflowFailureMapper.taxDeclarationFailure(
            new TaxDeclarationRejection.DefinitionViolations(
                List.of(
                    new TaxDefinitionViolation(
                        "unknown-payable-account",
                        "payableAccountCode",
                        "The payable account is not declared."))));

    assertEquals("tax-book-not-initialized", missingBook.code());
    assertEquals("The selected book does not exist or has not been opened.", missingBook.message());
    assertEquals(List.of(), missingBook.facts());
    assertEquals("tax-definition-violations", definitionViolations.code());
    assertEquals(
        List.of(BookWorkflowFact.count("violationCount", 1)), definitionViolations.facts());
  }
}
