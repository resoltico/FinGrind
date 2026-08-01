package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookLifecycleRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.DiagnosticDescriptor;
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
    return attestationRejectedEnvelope(
        failure, failure.admissionDiagnostic(AdmissionContext.ORDINARY_LIVE_ADMISSION));
  }

  /** Maps a registry-mutation refusal using its cataloged target-aware diagnostic. */
  static CliEnvelopeJsonModels.Envelope<?> attestationRegistryMutationRejectedEnvelope(
      AttestationVerificationFailure failure) {
    return attestationRejectedEnvelope(
        failure, failure.admissionDiagnostic(AdmissionContext.REGISTRY_MUTATION));
  }

  static CliEnvelopeJsonModels.Envelope<?> attestationBookVerificationRejectedEnvelope(
      AttestationVerificationFailure failure) {
    return attestationRejectedEnvelope(
        failure, failure.verificationDiagnostic(OperationId.VERIFY_BOOK));
  }

  static CliEnvelopeJsonModels.Envelope<?> attestationReviewVerificationRejectedEnvelope(
      AttestationVerificationFailure failure) {
    return attestationRejectedEnvelope(
        failure, failure.verificationDiagnostic(OperationId.ATTESTATION_REVIEW));
  }

  static CliEnvelopeJsonModels.Envelope<?> attestationReceiptExportVerificationRejectedEnvelope(
      AttestationVerificationFailure failure) {
    return attestationRejectedEnvelope(
        failure, failure.verificationDiagnostic(OperationId.EXPORT_ATTESTATION_RECEIPT));
  }

  static CliEnvelopeJsonModels.Envelope<?> attestationReceiptVerificationRejectedEnvelope(
      AttestationVerificationFailure failure) {
    return attestationRejectedEnvelope(
        failure, failure.verificationDiagnostic(OperationId.VERIFY_RECEIPT));
  }

  private static CliEnvelopeJsonModels.Envelope<?> attestationRejectedEnvelope(
      AttestationVerificationFailure failure, DiagnosticDescriptor diagnostic) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        failure.wireCode(),
        diagnostic.message(),
        diagnostic.hint(),
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  static CliEnvelopeJsonModels.Envelope<?> backupAcknowledgementAuthorizationRejectedEnvelope(
      BackupBookResult.AcknowledgementAuthorizationRejected rejected) {
    BackupBookResult.AcknowledgementAuthorizationRejected checkedRejected =
        java.util.Objects.requireNonNull(rejected, "rejected");
    AttestationVerificationFailure failure = checkedRejected.failure();
    DiagnosticDescriptor diagnostic =
        failure.admissionDiagnostic(AdmissionContext.BACKUP_ACKNOWLEDGEMENT);
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        failure.wireCode(),
        diagnostic.message(),
        diagnostic.hint(),
        null,
        null,
        new CliBookLifecycleRejectionJsonModels.BackupAcknowledgementAuthorizationRejectedDetails(
            CliPublicPaths.absoluteValue(checkedRejected.bookFilePath()),
            CliPublicPaths.absoluteValue(checkedRejected.backupFilePath()),
            CliPublicPaths.absoluteValue(checkedRejected.backupBookKeyFilePath()),
            checkedRejected.backupId().toString(),
            CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.from(
                checkedRejected.pairPublicationCompletion()),
            CliProtectedBookPairPublicationRetentionPresentation.payload(
                checkedRejected.pairPublicationRetention())),
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
