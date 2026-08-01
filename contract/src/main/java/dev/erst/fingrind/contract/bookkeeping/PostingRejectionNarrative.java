package dev.erst.fingrind.contract.bookkeeping;

import org.jspecify.annotations.Nullable;

/** Posting-rejection narrative catalog split out from the public rejection facade. */
final class PostingRejectionNarrative {
  private static final String GET_POSTING_OPERATION =
      dev.erst.fingrind.contract.protocol.ProtocolCatalog.operationName(
          dev.erst.fingrind.contract.protocol.OperationId.GET_POSTING);
  private static final String LIST_POSTINGS_OPERATION =
      dev.erst.fingrind.contract.protocol.ProtocolCatalog.operationName(
          dev.erst.fingrind.contract.protocol.OperationId.LIST_POSTINGS);

  private PostingRejectionNarrative() {}

  static String message(PostingRejection rejection) {
    return switch (rejection) {
      case FoundationalPostingRejection foundationalRejection ->
          foundationalMessage(foundationalRejection);
      case WorkflowPostingRejection workflowRejection -> workflowMessage(workflowRejection);
    };
  }

  private static String foundationalMessage(FoundationalPostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + RejectionNarrative.openBookOperation()
              + ".";
      case PostingRejection.AccountStateViolations violations ->
          AccountStateViolationOwner.envelopeMessage(violations.violations());
      case PostingRejection.EntrySemanticsViolations violations ->
          EntrySemanticsViolationOwner.envelopeMessage(violations.violations());
      case PostingRejection.IdempotencyKeyConflict _ ->
          "This idempotency key is already bound to a different committed posting request in this book.";
      case PostingEffectiveDateBeforeBookStart beforeBookStart ->
          "Posting effective date '%s' is before this book's immutable accounting start date '%s'."
              .formatted(
                  beforeBookStart.attemptedEffectiveDate(),
                  beforeBookStart.bookStartEffectiveDate());
      case PostingRejection.PostingEffectiveDateInFuture futureDate ->
          "Posting effective date '%s' is after current UTC date '%s'."
              .formatted(futureDate.attemptedEffectiveDate(), futureDate.currentUtcDate());
      case PostingRejection.BookFunctionalCurrencyMismatch functionalCurrencyMismatch ->
          "Journal-line currency '%s' does not match this book's functional currency '%s'."
              .formatted(
                  functionalCurrencyMismatch.attemptedCurrency().code(),
                  functionalCurrencyMismatch.functionalCurrency().code());
      case PostingRejection.SweptInterimResultViolation rejectionSweptInterimResult ->
          "Posting effective date '%s' falls inside the transferred-through horizon ending '%s'."
              .formatted(
                  rejectionSweptInterimResult.attemptedEffectiveDate(),
                  rejectionSweptInterimResult.transferredThroughEffectiveDate());
    };
  }

  private static String workflowMessage(WorkflowPostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.OpeningPositionWindowClosed rejectionWindowClosed ->
          "OPENING_POSITION is allowed only before the first committed posting in this book; the first blocking posting is '%s' on '%s'."
              .formatted(
                  rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
                  rejectionWindowClosed.firstBlockingEffectiveDate());
      case PostingRejection.OpeningPositionTouchesNominalAccount openingBalanceNominal ->
          "OPENING_POSITION may seed only asset, liability, or equity accounts; '%s' is '%s'."
              .formatted(
                  openingBalanceNominal.accountCode().value(),
                  openingBalanceNominal.accountType().wireValue());
      case PostingRejection.ReservedResultClassification rejectionReserved ->
          "Account '%s' uses close-reserved classification '%s' and is reserved for generated close postings."
              .formatted(
                  rejectionReserved.accountCode().value(),
                  rejectionReserved.financialPositionLineClassification().wireValue());
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          "No committed posting exists for reversal target '%s'."
              .formatted(reversalTargetNotFound.priorPostingId().value());
      case ReversalTargetIsReversal reversalTargetIsReversal ->
          "Posting '%s' is already one reversal posting, so it cannot be reversed."
              .formatted(reversalTargetIsReversal.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          "Posting '%s' already has a full reversal."
              .formatted(reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          "Reversal candidate does not negate posting '%s'."
              .formatted(reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  static @Nullable String hint(PostingRejection rejection) {
    return switch (rejection) {
      case FoundationalPostingRejection foundationalRejection ->
          foundationalHint(foundationalRejection);
      case WorkflowPostingRejection workflowRejection -> workflowHint(workflowRejection);
    };
  }

  private static @Nullable String foundationalHint(FoundationalPostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "Run "
              + RejectionNarrative.openBookOperation()
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case PostingRejection.AccountStateViolations _ -> null;
      case PostingRejection.EntrySemanticsViolations _ -> null;
      case PostingRejection.IdempotencyKeyConflict _ ->
          "Retry only with the exact same normalized request to receive an idempotent replay, or submit the changed request with a fresh provenance.idempotencyKey.";
      case PostingEffectiveDateBeforeBookStart beforeBookStart ->
          "Use an effective date on or after this book's immutable accounting start date '%s'."
              .formatted(beforeBookStart.bookStartEffectiveDate());
      case PostingRejection.PostingEffectiveDateInFuture _ ->
          "Use an effective date on or before the current UTC date.";
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          "Use the selected book's functional currency for every journal line in this request. If the business event happened in another currency, retain that transaction amount inside foreignExchange instead of changing the journal-line currency.";
      case PostingRejection.SweptInterimResultViolation _ ->
          "Use an effective date after the transferred-through horizon, or close the next contiguous reporting period before posting into later dates.";
    };
  }

  private static @Nullable String workflowHint(WorkflowPostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.OpeningPositionWindowClosed rejectionWindowClosed ->
          "OPENING_POSITION is only accepted before the first committed posting in the book. The window closed with "
              + rejectionWindowClosed.firstBlockingPostingKind().wireValue()
              + " on "
              + rejectionWindowClosed.firstBlockingEffectiveDate()
              + "; create a new book if the opening statement was not seeded completely.";
      case PostingRejection.OpeningPositionTouchesNominalAccount _ ->
          "OPENING_POSITION may seed only asset, liability, or equity accounts. Move revenue and expense setup into real operating-period postings instead.";
      case PostingRejection.ReservedResultClassification rejectionReserved ->
          "Post directly to ordinary accounts only; generated close operations own classification '"
              + rejectionReserved.financialPositionLineClassification().wireValue()
              + "'.";
      case PostingRejection.ReversalTargetNotFound _ ->
          "Use "
              + GET_POSTING_OPERATION
              + " or "
              + LIST_POSTINGS_OPERATION
              + " to confirm the prior posting id before retrying the reversal.";
      case ReversalTargetIsReversal _ ->
          "Post one fresh operational entry with its own evidence to restore the business effect instead of reversing a reversal.";
      case PostingRejection.ReversalAlreadyExists _ ->
          "Inspect the existing reversal for the referenced posting instead of retrying another reversal.";
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          "Build a full negating journal entry for the referenced posting so every line, amount, and side inverts the original exactly.";
    };
  }
}
