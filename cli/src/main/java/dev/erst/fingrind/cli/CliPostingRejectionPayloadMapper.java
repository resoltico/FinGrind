package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountStateViolationPayload;
import dev.erst.fingrind.cli.json.CliEntrySemanticsViolationPayload;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;

/** Maps posting rejections into the CLI rejected-envelope contract. */
final class CliPostingRejectionPayloadMapper {
  private CliPostingRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.RejectedEnvelope rejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolEnvelopeStatus.REJECTED,
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.hint(rejection),
        requestIdempotencyKey,
        rejectionDetails(rejection));
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
      case PostingRejection.OpenAccountingPositionWindowClosed rejectionWindowClosed ->
          new CliRejectionJsonModels.OpenAccountingPositionWindowClosedDetails(
              rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
              rejectionWindowClosed.firstBlockingEffectiveDate().toString());
      case PostingRejection.OpenAccountingPositionTouchesNominalAccount
              rejectionOpenAccountingPosition ->
          new CliRejectionJsonModels.OpenAccountingPositionNominalAccountDetails(
              rejectionOpenAccountingPosition.accountCode().value(),
              rejectionOpenAccountingPosition.accountType().wireValue());
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

  private static CliAccountStateViolationPayload accountStateViolationPayload(
      PostingRejection.AccountStateViolation violation) {
    PostingRejection.AccountStateViolationDetail detail =
        PostingRejection.accountStateDetail(violation);
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
