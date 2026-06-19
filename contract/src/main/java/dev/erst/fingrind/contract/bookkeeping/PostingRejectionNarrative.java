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
  private static final String TRANSFER_PERIOD_RESULT_OPERATION =
      dev.erst.fingrind.contract.protocol.ProtocolCatalog.operationName(
          dev.erst.fingrind.contract.protocol.OperationId.TRANSFER_PERIOD_RESULT);

  private PostingRejectionNarrative() {}

  static String message(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + RejectionNarrative.openBookOperation()
              + ".";
      case PostingRejection.AccountStateViolations violations ->
          AccountStateViolationOwner.envelopeMessage(violations.violations());
      case PostingRejection.EntrySemanticsViolations violations ->
          EntrySemanticsViolationOwner.envelopeMessage(violations.violations());
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

  static @Nullable String hint(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "Run "
              + RejectionNarrative.openBookOperation()
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case PostingRejection.AccountStateViolations _ -> null;
      case PostingRejection.EntrySemanticsViolations _ -> null;
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "Inspect the already-committed posting for this idempotency key instead of retrying the same key, or submit a new posting with a fresh provenance.idempotencyKey.";
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          "Use the selected book's functional currency for every journal line in this request, or open a separate book for another currency.";
      case PostingRejection.TransferredPeriodResultViolation _ ->
          "Use an effective date after the transferred-through horizon, or close the next contiguous reporting period before posting into later dates.";
      case PostingRejection.OpenAccountingPositionWindowClosed rejectionWindowClosed ->
          "OPEN_ACCOUNTING_POSITION is only accepted before the first committed posting in the book. The window closed with "
              + rejectionWindowClosed.firstBlockingPostingKind().wireValue()
              + " on "
              + rejectionWindowClosed.firstBlockingEffectiveDate()
              + "; create a new book if the opening statement was not seeded completely.";
      case PostingRejection.OpenAccountingPositionTouchesNominalAccount _ ->
          "OPEN_ACCOUNTING_POSITION may seed only asset, liability, or equity accounts. Move revenue and expense setup into real operating-period postings instead.";
      case PostingRejection.ResultHoldingAccountReserved _ ->
          "Post directly to ordinary accounts only; let "
              + TRANSFER_PERIOD_RESULT_OPERATION
              + " generate result-holding postings automatically.";
      case PostingRejection.ReversalTargetNotFound _ ->
          "Use "
              + GET_POSTING_OPERATION
              + " or "
              + LIST_POSTINGS_OPERATION
              + " to confirm the prior posting id before retrying the reversal.";
      case PostingRejection.ReversalAlreadyExists _ ->
          "Inspect the existing reversal for the referenced posting instead of retrying another reversal.";
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          "Build a full negating journal entry for the referenced posting so every line, amount, and side inverts the original exactly.";
    };
  }
}
