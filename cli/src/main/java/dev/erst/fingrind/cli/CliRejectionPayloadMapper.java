package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;

/** Maps deterministic rejection families into the CLI JSON envelope model. */
final class CliRejectionPayloadMapper {
  private CliRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.RejectedEnvelope postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return CliPostingRejectionPayloadMapper.rejectedEnvelope(requestIdempotencyKey, rejection);
  }

  static CliEnvelopeJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return CliAdministrationRejectionPayloadMapper.rejectedEnvelope(rejection);
  }

  static CliEnvelopeJsonModels.RejectedEnvelope maintenanceRejectedEnvelope(
      BookMaintenanceRejection rejection) {
    return CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(rejection);
  }

  static CliEnvelopeJsonModels.RejectedEnvelope queryRejectedEnvelope(
      BookQueryRejection rejection) {
    return CliQueryRejectionPayloadMapper.rejectedEnvelope(rejection);
  }
}
