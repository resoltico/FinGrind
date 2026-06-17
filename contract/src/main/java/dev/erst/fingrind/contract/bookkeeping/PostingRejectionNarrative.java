package dev.erst.fingrind.contract.bookkeeping;

/** Posting-rejection narrative catalog split out from the public rejection facade. */
final class PostingRejectionNarrative {
  private PostingRejectionNarrative() {}

  static String message(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + RejectionNarrative.openBookOperation()
              + ".";
      case PostingRejection.AccountStateViolations violations ->
          "Posting references undeclared, inactive, or non-postable accounts."
              + " Fix every issue in details.violations before retrying."
              + " Reported issues: "
              + violations.violations().size();
      case PostingRejection.EntrySemanticsViolations violations ->
          "Posting contradicts the published semantics of the selected entry kind."
              + " Fix every issue in details.violations before retrying."
              + " Reported issues: "
              + violations.violations().size();
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "A posting with the same idempotency key already exists in this book.";
      case PostingRejection.BookFunctionalCurrencyMismatch functionalCurrencyMismatch ->
          "Posting currency '%s' does not match this book's functional currency '%s'."
              .formatted(
                  functionalCurrencyMismatch.attemptedCurrency().code(),
                  functionalCurrencyMismatch.functionalCurrency().code());
      case PostingRejection.TransferredPeriodResultViolation rejectionTransferredPeriodResult ->
          "Posting effective date '%s' falls inside the transferred-through horizon ending '%s'."
              .formatted(
                  rejectionTransferredPeriodResult.attemptedEffectiveDate(),
                  rejectionTransferredPeriodResult.transferredThroughEffectiveDate());
      case PostingRejection.OpenAccountingPositionWindowClosed rejectionWindowClosed ->
          "OPEN_ACCOUNTING_POSITION is allowed only before the first committed posting in this book; the first blocking posting is '%s' on '%s'."
              .formatted(
                  rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
                  rejectionWindowClosed.firstBlockingEffectiveDate());
      case PostingRejection.OpenAccountingPositionTouchesNominalAccount openingBalanceNominal ->
          "OPEN_ACCOUNTING_POSITION may seed only asset, liability, or equity accounts; '%s' is '%s'."
              .formatted(
                  openingBalanceNominal.accountCode().value(),
                  openingBalanceNominal.accountType().wireValue());
      case PostingRejection.ResultHoldingAccountReserved rejectionReserved ->
          "Result-holding account '%s' is reserved for generated period-result-transfer postings."
              .formatted(rejectionReserved.accountCode().value());
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          "No committed posting exists for reversal target '%s'."
              .formatted(reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          "Posting '%s' already has a full reversal."
              .formatted(reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          "Reversal candidate does not negate posting '%s'."
              .formatted(reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }
}
