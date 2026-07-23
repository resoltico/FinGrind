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
    AuthorizationDiagnostic diagnostic = signingDiagnostic(failure);
    return authorizationRejectedEnvelope(failure, diagnostic);
  }

  /** Maps a registry-mutation refusal without misdescribing its target as a signer failure. */
  static CliEnvelopeJsonModels.Envelope<?> attestationRegistryMutationRejectedEnvelope(
      AttestationVerificationFailure failure) {
    return authorizationRejectedEnvelope(failure, registryMutationDiagnostic(failure));
  }

  private static CliEnvelopeJsonModels.Envelope<?> authorizationRejectedEnvelope(
      AttestationVerificationFailure failure, AuthorizationDiagnostic diagnostic) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        failure.wireCode(),
        diagnostic.message(),
        diagnostic.hint(),
        null,
        null,
        null,
        null);
  }

  private static AuthorizationDiagnostic signingDiagnostic(AttestationVerificationFailure failure) {
    return failure == AttestationVerificationFailure.POLICY_CAPACITY_INVALID
        ? new AuthorizationDiagnostic(
            "The requested authority change would leave one or more effective quorums impossible to satisfy.",
            "Inspect the current attestation policy, retain enough eligible principals for every quorum, then rerun the authority change.")
        : new AuthorizationDiagnostic(
            "The selected signing credentials do not authorize this attestation action at the live book head.",
            "Confirm the enrolled credential, credential purpose, capability grant, and quorum, then rerun the action.");
  }

  private static AuthorizationDiagnostic registryMutationDiagnostic(
      AttestationVerificationFailure failure) {
    return switch (failure) {
      case DUPLICATE_PRINCIPAL ->
          new AuthorizationDiagnostic(
              "The requested credential enrollment repeats a principal already represented in the current attestation registry.",
              "Use "
                  + OperationId.ROLLOVER_KEY.wireName()
                  + " for a replacement credential, or choose a principal ID not already enrolled.");
      case DUPLICATE_KEY ->
          new AuthorizationDiagnostic(
              "The requested credential is already represented in the current attestation registry.",
              "Generate a different credential for this enrollment or use the existing credential's principal.");
      case KEY_NOT_ENROLLED ->
          new AuthorizationDiagnostic(
              "The requested rollover or revocation target is not enrolled at the current attestation head.",
              "Run "
                  + OperationId.VERIFY_BOOK.wireName()
                  + " to inspect the current attestation registry, then select an enrolled credential.");
      case KEY_REVOKED ->
          new AuthorizationDiagnostic(
              "The requested rollover or revocation target is already revoked at the current attestation head.",
              "Run "
                  + OperationId.VERIFY_BOOK.wireName()
                  + " to inspect the current registry; select an active enrolled credential instead.");
      case KEY_SUPERSEDED ->
          new AuthorizationDiagnostic(
              "The requested rollover or revocation target was already superseded by a replacement credential at the current attestation head.",
              "Run "
                  + OperationId.VERIFY_BOOK.wireName()
                  + " to identify the active replacement credential; superseded credentials cannot be changed again.");
      case KEY_PRINCIPAL_MISMATCH ->
          new AuthorizationDiagnostic(
              "The requested credential belongs to a different principal in the current attestation registry.",
              "Use the principal ID bound to that credential, or select the intended principal's credential.");
      case POLICY_CAPACITY_INVALID -> signingDiagnostic(failure);
      default -> signingDiagnostic(failure);
    };
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

  private record AuthorizationDiagnostic(String message, String hint) {}
}
