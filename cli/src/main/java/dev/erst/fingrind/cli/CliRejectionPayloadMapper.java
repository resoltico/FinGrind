package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;

/** Maps deterministic rejection families into the CLI JSON envelope model. */
final class CliRejectionPayloadMapper {
  private CliRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.RejectedEnvelope postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        requestIdempotencyKey,
        postingRejectionDetails(rejection));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        null,
        administrationRejectionDetails(rejection));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope queryRejectedEnvelope(
      BookQueryRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        null,
        queryRejectionDetails(rejection));
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      postingRejectionDetails(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> null;
      case PostingRejection.AccountStateViolations violations ->
          new CliRejectionJsonModels.AccountStateViolationsDetails(
              violations.violations().stream()
                  .map(CliRejectionPayloadMapper::accountStateViolationPayload)
                  .toList());
      case PostingRejection.DuplicateIdempotencyKey _ -> null;
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
              PostingRejection.wireCode(unknownAccount), unknownAccount.accountCode().value());
      case PostingRejection.InactiveAccount inactiveAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(inactiveAccount), inactiveAccount.accountCode().value());
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      administrationRejectionDetails(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> null;
      case BookAdministrationRejection.BookNotInitialized _ -> null;
      case BookAdministrationRejection.BookContainsSchema _ -> null;
      case BookAdministrationRejection.NormalBalanceConflict conflict ->
          new CliRejectionJsonModels.NormalBalanceConflictDetails(
              conflict.accountCode().value(),
              conflict.existingNormalBalance().wireValue(),
              conflict.requestedNormalBalance().wireValue());
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      queryRejectionDetails(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ -> null;
      case BookQueryRejection.UnknownAccount unknownAccount ->
          new CliRejectionJsonModels.UnknownAccountDetails(unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          new CliRejectionJsonModels.PostingNotFoundDetails(postingNotFound.postingId().value());
    };
  }
}
