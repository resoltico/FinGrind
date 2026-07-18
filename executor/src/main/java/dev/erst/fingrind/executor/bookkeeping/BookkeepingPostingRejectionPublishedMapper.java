package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.InventoryMovementPrecedesAccountHorizon;
import dev.erst.fingrind.contract.bookkeeping.InventoryQuantityBelowZero;
import dev.erst.fingrind.contract.bookkeeping.InventoryWriteDownExceedsCarryingCost;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import java.util.Objects;

/** Owns publication of local posting-validation rejections. */
final class BookkeepingPostingRejectionPublishedMapper {
  private BookkeepingPostingRejectionPublishedMapper() {}

  static PostingRejection toPublished(BookkeepingPostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case FoundationalBookkeepingPostingRejection foundationalRejection ->
          foundationalPostingRejection(foundationalRejection);
      case WorkflowBookkeepingPostingRejection workflowRejection ->
          workflowPostingRejection(workflowRejection);
    };
  }

  private static PostingRejection foundationalPostingRejection(
      FoundationalBookkeepingPostingRejection rejection) {
    return switch (rejection) {
      case BookkeepingPostingRejection.BookNotInitialized _ ->
          new PostingRejection.BookNotInitialized();
      case BookkeepingPostingRejection.AccountStateViolations violations ->
          new PostingRejection.AccountStateViolations(
              violations.violations().stream()
                  .map(BookkeepingPostingRejectionPublishedMapper::toPublished)
                  .toList());
      case BookkeepingPostingRejection.EntrySemanticsViolations violations ->
          new PostingRejection.EntrySemanticsViolations(
              violations.violations().stream()
                  .map(BookkeepingPostingRejectionPublishedMapper::toPublished)
                  .toList());
      case BookkeepingPostingRejection.IdempotencyKeyConflict _ ->
          new PostingRejection.IdempotencyKeyConflict();
      case BookkeepingPostingEffectiveDateBeforeBookStart beforeBookStart ->
          new PostingEffectiveDateBeforeBookStart(
              beforeBookStart.attemptedEffectiveDate(), beforeBookStart.bookStartEffectiveDate());
      case BookkeepingPostingRejection.PostingEffectiveDateInFuture futureDate ->
          new PostingRejection.PostingEffectiveDateInFuture(
              futureDate.attemptedEffectiveDate(), futureDate.currentUtcDate());
      case BookkeepingPostingRejection.BookFunctionalCurrencyMismatch currencyMismatch ->
          new PostingRejection.BookFunctionalCurrencyMismatch(
              currencyMismatch.functionalCurrency(), currencyMismatch.attemptedCurrency());
      case BookkeepingPostingRejection.SweptInterimResultViolation rejectionClosedPeriod ->
          new PostingRejection.SweptInterimResultViolation(
              rejectionClosedPeriod.transferredThroughEffectiveDate(),
              rejectionClosedPeriod.attemptedEffectiveDate());
    };
  }

  private static PostingRejection workflowPostingRejection(
      WorkflowBookkeepingPostingRejection rejection) {
    return switch (rejection) {
      case BookkeepingPostingRejection.OpeningPositionWindowClosed rejectionWindowClosed ->
          new PostingRejection.OpeningPositionWindowClosed(
              rejectionWindowClosed.firstBlockingPostingKind(),
              rejectionWindowClosed.firstBlockingEffectiveDate());
      case BookkeepingPostingRejection.OpeningPositionTouchesNominalAccount rejectionNominal ->
          new PostingRejection.OpeningPositionTouchesNominalAccount(
              rejectionNominal.accountCode(), rejectionNominal.accountType());
      case BookkeepingPostingRejection.ReservedResultClassification rejectionReserved ->
          new PostingRejection.ReservedResultClassification(
              rejectionReserved.accountCode(),
              rejectionReserved.financialPositionLineClassification());
      case BookkeepingPostingRejection.ReversalTargetNotFound rejectionTarget ->
          new PostingRejection.ReversalTargetNotFound(rejectionTarget.priorPostingId());
      case ReversalTargetIsReversal rejectionTarget ->
          new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
              rejectionTarget.priorPostingId());
      case BookkeepingPostingRejection.ReversalAlreadyExists rejectionExists ->
          new PostingRejection.ReversalAlreadyExists(rejectionExists.priorPostingId());
      case BookkeepingPostingRejection.ReversalDoesNotNegateTarget rejectionMismatch ->
          new PostingRejection.ReversalDoesNotNegateTarget(rejectionMismatch.priorPostingId());
    };
  }

  private static PostingRejection.AccountStateViolation toPublished(
      BookkeepingPostingRejection.AccountStateViolation violation) {
    Objects.requireNonNull(violation, "violation");
    return switch (violation) {
      case BookkeepingPostingRejection.UnknownAccount unknownAccount ->
          new PostingRejection.UnknownAccount(unknownAccount.accountCode());
      case BookkeepingPostingRejection.InactiveAccount inactiveAccount ->
          new PostingRejection.InactiveAccount(inactiveAccount.accountCode());
      case BookkeepingPostingRejection.NonPostableAccount nonPostableAccount ->
          new PostingRejection.NonPostableAccount(
              nonPostableAccount.accountCode(), nonPostableAccount.accountNodeKind());
      case InventoryMovementPrecedesAccountHorizonViolation horizonViolation ->
          new InventoryMovementPrecedesAccountHorizon(
              horizonViolation.accountCode(),
              horizonViolation.field(),
              horizonViolation.attemptedEffectiveDate(),
              horizonViolation.accountHorizonEffectiveDate());
      case InventoryQuantityBelowZeroViolation quantityViolation ->
          new InventoryQuantityBelowZero(
              quantityViolation.accountCode(),
              quantityViolation.field(),
              quantityViolation.effectiveDate(),
              quantityViolation.quantityOnHand(),
              quantityViolation.requestedDecreaseQuantity(),
              quantityViolation.resultingShortfallQuantity());
      case InventoryWriteDownExceedsCarryingCostViolation carryingCostViolation ->
          new InventoryWriteDownExceedsCarryingCost(
              carryingCostViolation.accountCode(),
              carryingCostViolation.field(),
              carryingCostViolation.effectiveDate(),
              carryingCostViolation.carryingCostOnHand(),
              carryingCostViolation.requestedCostDecrease(),
              carryingCostViolation.resultingCostShortfall());
    };
  }

  private static PostingRejection.EntrySemanticsViolation toPublished(
      BookkeepingPostingRejection.EntrySemanticsViolation violation) {
    Objects.requireNonNull(violation, "violation");
    return new PostingRejection.EntrySemanticsViolation(
        violation.code(), violation.field(), violation.message());
  }
}
