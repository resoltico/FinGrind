package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import org.jspecify.annotations.Nullable;

/** Maps deterministic rejection families into the CLI JSON envelope model. */
final class CliRejectionPayloadMapper {
  private CliRejectionPayloadMapper() {}

  static CliResponseJsonModels.RejectedEnvelope postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return new CliResponseJsonModels.RejectedEnvelope(
        ProtocolStatuses.REJECTED,
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        requestIdempotencyKey,
        postingRejectionDetails(rejection));
  }

  static CliResponseJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return new CliResponseJsonModels.RejectedEnvelope(
        ProtocolStatuses.REJECTED,
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        null,
        administrationRejectionDetails(rejection));
  }

  static CliResponseJsonModels.RejectedEnvelope queryRejectedEnvelope(
      BookQueryRejection rejection) {
    return new CliResponseJsonModels.RejectedEnvelope(
        ProtocolStatuses.REJECTED,
        BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        null,
        queryRejectionDetails(rejection));
  }

  private static @Nullable Object postingRejectionDetails(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> null;
      case PostingRejection.AccountStateViolations violations ->
          new CliResponseJsonModels.AccountStateViolationsDetails(
              violations.violations().stream()
                  .map(CliRejectionPayloadMapper::accountStateViolationPayload)
                  .toList());
      case PostingRejection.DuplicateIdempotencyKey _ -> null;
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          new CliResponseJsonModels.PriorPostingDetails(
              reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          new CliResponseJsonModels.PriorPostingDetails(
              reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          new CliResponseJsonModels.PriorPostingDetails(
              reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static CliResponseJsonModels.AccountStateViolationPayload accountStateViolationPayload(
      PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount ->
          new CliResponseJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(unknownAccount), unknownAccount.accountCode().value());
      case PostingRejection.InactiveAccount inactiveAccount ->
          new CliResponseJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(inactiveAccount), inactiveAccount.accountCode().value());
    };
  }

  private static @Nullable Object administrationRejectionDetails(
      BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> null;
      case BookAdministrationRejection.BookNotInitialized _ -> null;
      case BookAdministrationRejection.BookContainsSchema _ -> null;
      case BookAdministrationRejection.NormalBalanceConflict conflict ->
          new CliResponseJsonModels.NormalBalanceConflictDetails(
              conflict.accountCode().value(),
              conflict.existingNormalBalance().wireValue(),
              conflict.requestedNormalBalance().wireValue());
    };
  }

  private static @Nullable Object queryRejectionDetails(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ -> null;
      case BookQueryRejection.UnknownAccount unknownAccount ->
          new CliResponseJsonModels.UnknownAccountDetails(unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          new CliResponseJsonModels.PostingNotFoundDetails(postingNotFound.postingId().value());
    };
  }
}
