package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountStateViolationPayload;
import dev.erst.fingrind.cli.json.CliEntrySemanticsViolationPayload;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountStateViolationDetail;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;

/** Maps posting rejections into the CLI rejected-envelope contract. */
final class CliPostingRejectionPayloadMapper {
  private CliPostingRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.Envelope<?> rejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.hint(rejection),
        null,
        requestIdempotencyKey,
        rejectionDetails(rejection),
        null);
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      rejectionDetails(PostingRejection rejection) {
    CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails bookkeepingDetails =
        bookkeepingDetails(rejection);
    return bookkeepingDetails != null ? bookkeepingDetails : reversalDetails(rejection);
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      bookkeepingDetails(PostingRejection rejection) {
    if (rejection instanceof PostingRejection.BookNotInitialized
        || rejection instanceof PostingRejection.IdempotencyKeyConflict) {
      return null;
    }
    return switch (rejection) {
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
      case PostingEffectiveDateBeforeBookStart beforeBookStart ->
          new CliRejectionJsonModels.PostingEffectiveDateBeforeBookStartDetails(
              beforeBookStart.attemptedEffectiveDate().toString(),
              beforeBookStart.bookStartEffectiveDate().toString());
      case PostingRejection.PostingEffectiveDateInFuture futureDate ->
          new CliRejectionJsonModels.PostingEffectiveDateInFutureDetails(
              futureDate.attemptedEffectiveDate().toString(),
              futureDate.currentUtcDate().toString());
      case PostingRejection.BookFunctionalCurrencyMismatch rejectionCurrencyMismatch ->
          new CliRejectionJsonModels.FunctionalCurrencyMismatchDetails(
              rejectionCurrencyMismatch.functionalCurrency().code(),
              rejectionCurrencyMismatch.attemptedCurrency().code());
      case PostingRejection.SweptInterimResultViolation violation ->
          new CliRejectionJsonModels.SweptInterimResultViolationDetails(
              violation.transferredThroughEffectiveDate().toString(),
              violation.attemptedEffectiveDate().toString());
      case PostingRejection.OpeningPositionWindowClosed rejectionWindowClosed ->
          new CliRejectionJsonModels.OpeningPositionWindowClosedDetails(
              rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
              rejectionWindowClosed.firstBlockingEffectiveDate().toString());
      case PostingRejection.OpeningPositionTouchesNominalAccount rejectionOpeningPosition ->
          new CliRejectionJsonModels.OpeningPositionNominalAccountDetails(
              rejectionOpeningPosition.accountCode().value(),
              rejectionOpeningPosition.accountType().wireValue());
      case PostingRejection.ReservedResultClassification rejectionReserved ->
          new CliRejectionJsonModels.ReservedResultClassificationDetails(
              rejectionReserved.accountCode().value(),
              rejectionReserved.financialPositionLineClassification().wireValue());
      default -> null;
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      reversalDetails(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          priorPostingDetails(reversalTargetNotFound.priorPostingId().value());
      case ReversalTargetIsReversal reversalTargetIsReversal ->
          priorPostingDetails(reversalTargetIsReversal.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          priorPostingDetails(reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          priorPostingDetails(reversalDoesNotNegateTarget.priorPostingId().value());
      default -> null;
    };
  }

  private static CliRejectionJsonModels.PriorPostingDetails priorPostingDetails(
      String priorPostingId) {
    return new CliRejectionJsonModels.PriorPostingDetails(priorPostingId);
  }

  private static CliAccountStateViolationPayload accountStateViolationPayload(
      PostingRejection.AccountStateViolation violation) {
    AccountStateViolationDetail detail = PostingRejection.accountStateDetail(violation);
    return new CliAccountStateViolationPayload(
        detail.code(),
        detail.field(),
        detail.message(),
        detail.category(),
        detail.repair(),
        detail.accountCode(),
        detail.accountNodeKind());
  }

  private static CliEntrySemanticsViolationPayload entrySemanticsViolationPayload(
      PostingRejection.EntrySemanticsViolation violation) {
    return new CliEntrySemanticsViolationPayload(
        violation.code(),
        violation.field(),
        violation.message(),
        violation.category(),
        violation.repair());
  }
}
