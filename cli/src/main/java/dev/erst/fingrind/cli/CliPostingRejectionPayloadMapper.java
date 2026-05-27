package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;

/** Maps posting rejections into the CLI rejected-envelope contract. */
final class CliPostingRejectionPayloadMapper {
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);
  private static final String GET_POSTING_OPERATION =
      ProtocolCatalog.operationName(OperationId.GET_POSTING);
  private static final String LIST_POSTINGS_OPERATION =
      ProtocolCatalog.operationName(OperationId.LIST_POSTINGS);
  private static final String TRANSFER_PERIOD_RESULT_OPERATION =
      ProtocolCatalog.operationName(OperationId.TRANSFER_PERIOD_RESULT);

  private CliPostingRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.RejectedEnvelope rejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolEnvelopeStatus.REJECTED,
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        rejectionHint(rejection),
        requestIdempotencyKey,
        rejectionDetails(rejection));
  }

  private static String rejectionHint(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "Run "
              + OPEN_BOOK_OPERATION
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case PostingRejection.AccountStateViolations _ ->
          "Declare or reactivate every account named in details.violations, then rerun the request with a fresh provenance.idempotencyKey.";
      case PostingRejection.EntrySemanticsViolations _ ->
          "Choose accounts and source-document types that match the selected entry kind, then rerun the request with a fresh provenance.idempotencyKey.";
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "Inspect the already-committed posting for this idempotency key instead of retrying the same key, or submit a new posting with a fresh provenance.idempotencyKey.";
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          "Use the selected book's functional currency for every journal line in this request, or open a separate book for another currency.";
      case PostingRejection.TransferredPeriodResultViolation _ ->
          "Use an effective date after the transferred-through horizon, or close the next contiguous reporting period before posting into later dates.";
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          "Opening balances are only accepted before the first committed posting in the book. The window closed with "
              + rejectionWindowClosed.firstBlockingPostingKind().wireValue()
              + " on "
              + rejectionWindowClosed.firstBlockingEffectiveDate()
              + "; create a new book if the opening statement was not seeded completely.";
      case PostingRejection.OpeningBalanceTouchesNominalAccount _ ->
          "Opening-balance postings may seed only asset, liability, or equity accounts. Move revenue and expense setup into real operating-period postings instead.";
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

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      rejectionDetails(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> null;
      case PostingRejection.AccountStateViolations violations ->
          new CliRejectionJsonModels.AccountStateViolationsDetails(
              violations.violations().stream()
                  .map(CliPostingRejectionPayloadMapper::accountStateViolationPayload)
                  .toList());
      case PostingRejection.EntrySemanticsViolations violations ->
          new CliRejectionJsonModels.EntrySemanticsViolationsDetails(
              violations.violations().stream()
                  .map(CliPostingRejectionPayloadMapper::entrySemanticsViolationPayload)
                  .toList());
      case PostingRejection.DuplicateIdempotencyKey _ -> null;
      case PostingRejection.BookFunctionalCurrencyMismatch rejectionCurrencyMismatch ->
          new CliRejectionJsonModels.FunctionalCurrencyMismatchDetails(
              rejectionCurrencyMismatch.functionalCurrency().code(),
              rejectionCurrencyMismatch.attemptedCurrency().code());
      case PostingRejection.TransferredPeriodResultViolation violation ->
          new CliRejectionJsonModels.TransferredPeriodResultViolationDetails(
              violation.transferredThroughEffectiveDate().toString(),
              violation.attemptedEffectiveDate().toString());
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          new CliRejectionJsonModels.OpeningBalanceWindowClosedDetails(
              rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
              rejectionWindowClosed.firstBlockingEffectiveDate().toString());
      case PostingRejection.OpeningBalanceTouchesNominalAccount rejectionOpeningBalance ->
          new CliRejectionJsonModels.OpeningBalanceNominalAccountDetails(
              rejectionOpeningBalance.accountCode().value(),
              rejectionOpeningBalance.accountType().wireValue());
      case PostingRejection.ResultHoldingAccountReserved rejectionReserved ->
          new CliRejectionJsonModels.ResultHoldingAccountDetails(
              rejectionReserved.accountCode().value());
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static CliRejectionJsonModels.AccountStateViolationPayload accountStateViolationPayload(
      PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(unknownAccount),
              unknownAccount.accountCode().value(),
              null);
      case PostingRejection.InactiveAccount inactiveAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(inactiveAccount),
              inactiveAccount.accountCode().value(),
              null);
      case PostingRejection.NonPostableAccount nonPostableAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(nonPostableAccount),
              nonPostableAccount.accountCode().value(),
              nonPostableAccount.accountNodeKind().wireValue());
    };
  }

  private static CliRejectionJsonModels.EntrySemanticsViolationPayload
      entrySemanticsViolationPayload(PostingRejection.EntrySemanticsViolation violation) {
    return new CliRejectionJsonModels.EntrySemanticsViolationPayload(
        violation.code(), violation.field(), violation.message());
  }
}
