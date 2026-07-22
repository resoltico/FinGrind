package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
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

  static CliEnvelopeJsonModels.Envelope<?> attestationAuthorizationRejectedEnvelope(
      AttestationVerificationFailure failure) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        failure.wireCode(),
        "The selected signing credentials do not authorize this attestation action at the live book head.",
        "Confirm the enrolled credential, credential purpose, capability grant, and quorum, then rerun the action.",
        null,
        null,
        null,
        null);
  }

  static CliEnvelopeJsonModels.Envelope<?> backupAcknowledgementAuthorizationRejectedEnvelope(
      AttestationVerificationFailure failure) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        failure.wireCode(),
        "The backup artifact was published, but the selected signing credentials do not authorize its source-book acknowledgement at the live book head.",
        "Retain the published backup pair. Confirm the enrolled credential, credential purpose, capability grant, and quorum, then rerun "
            + OperationId.BACKUP_BOOK.wireName()
            + " with the same backup ID.",
        null,
        null,
        null,
        null);
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
