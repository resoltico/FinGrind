package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.InventoryMovementPrecedesAccountHorizon;
import dev.erst.fingrind.contract.bookkeeping.InventoryQuantityBelowZero;
import dev.erst.fingrind.contract.bookkeeping.InventoryWriteDownExceedsCarryingCost;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Projects published posting rejections into stable ledger-plan workflow fact groups. */
final class LedgerPlanWorkflowPostingFailureFacts {
  private LedgerPlanWorkflowPostingFailureFacts() {}

  static List<BookWorkflowFact> postingRejectionFacts(PostingRejection rejection) {
    PostingRejection normalizedRejection = Objects.requireNonNull(rejection, "rejection");
    if (normalizedRejection instanceof PostingRejection.BookNotInitialized
        || normalizedRejection instanceof PostingRejection.IdempotencyKeyConflict) {
      return List.of();
    }
    if (normalizedRejection instanceof PostingRejection.AccountStateViolations violations) {
      return accountStateFacts(violations);
    }
    if (normalizedRejection instanceof PostingRejection.EntrySemanticsViolations violations) {
      return entrySemanticsFacts(violations);
    }
    return scalarOrPriorPostingFacts(normalizedRejection);
  }

  private static List<BookWorkflowFact> scalarOrPriorPostingFacts(PostingRejection rejection) {
    if (rejection instanceof PostingEffectiveDateBeforeBookStart beforeBookStart) {
      return List.of(
          BookWorkflowFact.text(
              "attemptedEffectiveDate", beforeBookStart.attemptedEffectiveDate().toString()),
          BookWorkflowFact.text(
              "bookStartEffectiveDate", beforeBookStart.bookStartEffectiveDate().toString()));
    }
    if (rejection instanceof PostingRejection.PostingEffectiveDateInFuture futureDate) {
      return List.of(
          BookWorkflowFact.text(
              "attemptedEffectiveDate", futureDate.attemptedEffectiveDate().toString()),
          BookWorkflowFact.text("currentUtcDate", futureDate.currentUtcDate().toString()));
    }
    if (rejection instanceof PostingRejection.BookFunctionalCurrencyMismatch mismatch) {
      return List.of(
          BookWorkflowFact.text("functionalCurrency", mismatch.functionalCurrency().code()),
          BookWorkflowFact.text("attemptedCurrency", mismatch.attemptedCurrency().code()));
    }
    if (rejection
        instanceof PostingRejection.SweptInterimResultViolation sweptInterimResultViolation) {
      return List.of(
          BookWorkflowFact.text(
              "transferredThroughEffectiveDate",
              sweptInterimResultViolation.transferredThroughEffectiveDate().toString()),
          BookWorkflowFact.text(
              "attemptedEffectiveDate",
              sweptInterimResultViolation.attemptedEffectiveDate().toString()));
    }
    if (rejection
        instanceof PostingRejection.OpeningPositionWindowClosed openingBalanceWindowClosed) {
      return List.of(
          BookWorkflowFact.text(
              "firstBlockingPostingKind",
              openingBalanceWindowClosed.firstBlockingPostingKind().wireValue()),
          BookWorkflowFact.text(
              "firstBlockingEffectiveDate",
              openingBalanceWindowClosed.firstBlockingEffectiveDate().toString()));
    }
    if (rejection
        instanceof PostingRejection.OpeningPositionTouchesNominalAccount openingBalanceNominal) {
      return List.of(
          BookWorkflowFact.text("accountCode", openingBalanceNominal.accountCode().value()),
          BookWorkflowFact.text("accountType", openingBalanceNominal.accountType().wireValue()));
    }
    if (rejection instanceof PostingRejection.ReservedResultClassification reservedClassification) {
      return List.of(
          BookWorkflowFact.text("accountCode", reservedClassification.accountCode().value()),
          BookWorkflowFact.text(
              "financialPositionLineClassification",
              reservedClassification.financialPositionLineClassification().wireValue()));
    }
    if (rejection instanceof PostingRejection.ReversalTargetNotFound reversalTargetNotFound) {
      return priorPostingFacts(reversalTargetNotFound.priorPostingId());
    }
    if (rejection instanceof ReversalTargetIsReversal reversalTargetIsReversal) {
      return priorPostingFacts(reversalTargetIsReversal.priorPostingId());
    }
    if (rejection instanceof PostingRejection.ReversalAlreadyExists reversalAlreadyExists) {
      return priorPostingFacts(reversalAlreadyExists.priorPostingId());
    }
    PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget =
        (PostingRejection.ReversalDoesNotNegateTarget) rejection;
    return priorPostingFacts(reversalDoesNotNegateTarget.priorPostingId());
  }

  private static List<BookWorkflowFact> accountStateFacts(
      PostingRejection.AccountStateViolations violations) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.count("violationCount", violations.violations().size()));
    for (PostingRejection.AccountStateViolation violation : violations.violations()) {
      facts.add(BookWorkflowFact.group("violation", accountStateViolationFacts(violation)));
    }
    return List.copyOf(facts);
  }

  private static List<BookWorkflowFact> accountStateViolationFacts(
      PostingRejection.AccountStateViolation violation) {
    dev.erst.fingrind.contract.bookkeeping.AccountStateViolationDetail detail =
        PostingRejection.accountStateDetail(violation);
    List<BookWorkflowFact> detailFacts = baseAccountStateViolationFacts(detail);
    switch (violation) {
      case PostingRejection.UnknownAccount _ -> {}
      case PostingRejection.InactiveAccount _ -> {}
      case PostingRejection.NonPostableAccount nonPostableAccount ->
          detailFacts.add(
              BookWorkflowFact.text(
                  "accountNodeKind", nonPostableAccount.accountNodeKind().wireValue()));
      case InventoryMovementPrecedesAccountHorizon horizonViolation ->
          addInventoryMovementHorizonFacts(detailFacts, horizonViolation);
      case InventoryQuantityBelowZero quantityViolation ->
          addInventoryQuantityBelowZeroFacts(detailFacts, quantityViolation);
      case InventoryWriteDownExceedsCarryingCost carryingCostViolation ->
          addInventoryWriteDownFacts(detailFacts, carryingCostViolation);
    }
    return List.copyOf(detailFacts);
  }

  private static List<BookWorkflowFact> baseAccountStateViolationFacts(
      dev.erst.fingrind.contract.bookkeeping.AccountStateViolationDetail detail) {
    List<BookWorkflowFact> detailFacts = new ArrayList<>();
    detailFacts.add(BookWorkflowFact.text("code", detail.code()));
    detailFacts.add(BookWorkflowFact.text("field", detail.field()));
    detailFacts.add(BookWorkflowFact.text("message", detail.message()));
    detailFacts.add(BookWorkflowFact.text("category", detail.category()));
    detailFacts.add(BookWorkflowFact.text("repair", detail.repair()));
    detailFacts.add(BookWorkflowFact.text("accountCode", detail.accountCode()));
    return detailFacts;
  }

  private static void addInventoryMovementHorizonFacts(
      List<BookWorkflowFact> detailFacts, InventoryMovementPrecedesAccountHorizon violation) {
    detailFacts.add(
        BookWorkflowFact.text(
            "attemptedEffectiveDate", violation.attemptedEffectiveDate().toString()));
    detailFacts.add(
        BookWorkflowFact.text(
            "accountHorizonEffectiveDate", violation.accountHorizonEffectiveDate().toString()));
  }

  private static void addInventoryQuantityBelowZeroFacts(
      List<BookWorkflowFact> detailFacts, InventoryQuantityBelowZero violation) {
    detailFacts.add(BookWorkflowFact.text("effectiveDate", violation.effectiveDate().toString()));
    detailFacts.add(
        BookWorkflowFact.text("quantityOnHand", quantityText(violation.quantityOnHand())));
    detailFacts.add(
        BookWorkflowFact.text(
            "requestedDecreaseQuantity", quantityText(violation.requestedDecreaseQuantity())));
    detailFacts.add(
        BookWorkflowFact.text(
            "resultingShortfallQuantity", quantityText(violation.resultingShortfallQuantity())));
  }

  private static void addInventoryWriteDownFacts(
      List<BookWorkflowFact> detailFacts, InventoryWriteDownExceedsCarryingCost violation) {
    detailFacts.add(BookWorkflowFact.text("effectiveDate", violation.effectiveDate().toString()));
    detailFacts.add(
        BookWorkflowFact.money(
            "carryingCostOnHand", MonetaryAmount.of(violation.carryingCostOnHand())));
    detailFacts.add(
        BookWorkflowFact.money(
            "requestedCostDecrease", MonetaryAmount.of(violation.requestedCostDecrease())));
    detailFacts.add(
        BookWorkflowFact.money(
            "resultingCostShortfall", MonetaryAmount.of(violation.resultingCostShortfall())));
  }

  private static String quantityText(Quantity quantity) {
    return quantity.canonicalDecimal();
  }

  private static List<BookWorkflowFact> entrySemanticsFacts(
      PostingRejection.EntrySemanticsViolations violations) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.count("violationCount", violations.violations().size()));
    for (PostingRejection.EntrySemanticsViolation violation : violations.violations()) {
      facts.add(entrySemanticsViolationFact(violation));
    }
    return List.copyOf(facts);
  }

  private static BookWorkflowFact entrySemanticsViolationFact(
      PostingRejection.EntrySemanticsViolation violation) {
    List<BookWorkflowFact> detailFacts = new ArrayList<>();
    detailFacts.add(BookWorkflowFact.text("code", violation.code()));
    if (violation.field() != null) {
      detailFacts.add(BookWorkflowFact.text("field", violation.field()));
    }
    detailFacts.add(BookWorkflowFact.text("message", violation.message()));
    return BookWorkflowFact.group("violation", detailFacts);
  }

  private static List<BookWorkflowFact> priorPostingFacts(PostingId priorPostingId) {
    return List.of(BookWorkflowFact.text("priorPostingId", priorPostingId.value()));
  }
}
