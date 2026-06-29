package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;

/** Maps deterministic rejection families into the CLI JSON envelope model. */
final class CliRejectionPayloadMapper {
  private CliRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.Envelope<?> postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return CliPostingRejectionPayloadMapper.rejectedEnvelope(requestIdempotencyKey, rejection);
  }

  static CliEnvelopeJsonModels.Envelope<?> administrationRejectedEnvelope(
      OperationId operationId, BookAdministrationRejection rejection) {
    return CliAdministrationRejectionPayloadMapper.rejectedEnvelope(operationId, rejection);
  }

  static CliEnvelopeJsonModels.Envelope<?> maintenanceRejectedEnvelope(
      BookMaintenanceRejection rejection) {
    return CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(rejection);
  }

  static CliEnvelopeJsonModels.Envelope<?> queryRejectedEnvelope(BookQueryRejection rejection) {
    return CliQueryRejectionPayloadMapper.rejectedEnvelope(rejection);
  }

  static CliEnvelopeJsonModels.Envelope<?> taxDeclarationRejectedEnvelope(
      TaxDeclarationRejection rejection) {
    return CliTaxRejectionPayloadMapper.declarationRejectedEnvelope(rejection);
  }

  static CliEnvelopeJsonModels.Envelope<?> taxQueryRejectedEnvelope(
      OperationId operationId, TaxQueryRejection rejection) {
    return CliTaxRejectionPayloadMapper.queryRejectedEnvelope(operationId, rejection);
  }
}
