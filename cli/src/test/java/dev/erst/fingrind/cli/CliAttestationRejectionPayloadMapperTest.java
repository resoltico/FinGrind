package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for catalog-driven attestation rejection payload mapping. */
class CliAttestationRejectionPayloadMapperTest {
  @Test
  void attestationAdmissionEnvelopes_projectEveryExactPublishedContextCatalog() {
    for (var context : AttestationVerificationFailure.admissionDiagnosticContexts()) {
      for (var diagnostic : context.diagnostics()) {
        AttestationVerificationFailure failure =
            AttestationVerificationFailure.fromWireCode(diagnostic.code());
        assertDiagnostic(
            admissionEnvelope(failure, context.context()),
            diagnostic.code(),
            diagnostic.message(),
            diagnostic.hint());
      }
    }
    for (AdmissionContext context : AdmissionContext.values()) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              admissionEnvelope(AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID, context));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            admissionEnvelope(
                AttestationVerificationFailure.MANIFEST_INVALID,
                AdmissionContext.REGISTRY_MUTATION));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            admissionEnvelope(
                AttestationVerificationFailure.RECEIPT_INVALID,
                AdmissionContext.REGISTRY_MUTATION));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            admissionEnvelope(
                AttestationVerificationFailure.MANIFEST_INVALID,
                AdmissionContext.BACKUP_ACKNOWLEDGEMENT));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            admissionEnvelope(
                AttestationVerificationFailure.RECEIPT_INVALID,
                AdmissionContext.BACKUP_ACKNOWLEDGEMENT));
  }

  @Test
  void historicalVerificationEnvelopes_projectTheExactPublishedSurfaceCatalog() {
    for (var surface : AttestationVerificationFailure.verificationDiagnosticSurfaces()) {
      for (var diagnostic : surface.diagnostics()) {
        AttestationVerificationFailure failure =
            AttestationVerificationFailure.fromWireCode(diagnostic.code());
        assertDiagnostic(
            verificationEnvelope(failure, surface.surface()),
            diagnostic.code(),
            diagnostic.message(),
            diagnostic.hint());
      }
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliRejectionPayloadMapper.attestationBookVerificationRejectedEnvelope(
                AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliRejectionPayloadMapper.attestationReviewVerificationRejectedEnvelope(
                AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliRejectionPayloadMapper.attestationReceiptExportVerificationRejectedEnvelope(
                AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID));
  }

  @Test
  void attestationRegistryMutationRejectedEnvelope_usesTargetAwareCatalogEntries() {
    assertRegistryMutationTargetDiagnostic(AttestationVerificationFailure.DUPLICATE_PRINCIPAL);
    assertRegistryMutationTargetDiagnostic(AttestationVerificationFailure.DUPLICATE_KEY);
    assertRegistryMutationTargetDiagnostic(AttestationVerificationFailure.KEY_NOT_ENROLLED);
    assertRegistryMutationTargetDiagnostic(AttestationVerificationFailure.KEY_REVOKED);
    assertRegistryMutationTargetDiagnostic(AttestationVerificationFailure.KEY_SUPERSEDED);
    assertRegistryMutationTargetDiagnostic(AttestationVerificationFailure.KEY_PRINCIPAL_MISMATCH);
  }

  private static void assertRegistryMutationTargetDiagnostic(
      AttestationVerificationFailure failure) {
    var ordinary = failure.admissionDiagnostic(AdmissionContext.ORDINARY_LIVE_ADMISSION);
    var registry = failure.admissionDiagnostic(AdmissionContext.REGISTRY_MUTATION);

    assertNotEquals(ordinary.message(), registry.message());
    assertNotEquals(ordinary.hint(), registry.hint());
    assertDiagnostic(
        CliRejectionPayloadMapper.attestationRegistryMutationRejectedEnvelope(failure),
        registry.code(),
        registry.message(),
        registry.hint());
  }

  private static void assertDiagnostic(
      CliEnvelopeJsonModels.Envelope<?> envelope,
      String expectedCode,
      String expectedMessage,
      String expectedHint) {
    assertEquals(expectedCode, envelope.code());
    assertEquals(expectedMessage, envelope.message());
    assertEquals(expectedHint, envelope.hint());
  }

  private static CliEnvelopeJsonModels.Envelope<?> verificationEnvelope(
      AttestationVerificationFailure failure, OperationId surface) {
    if (surface == OperationId.VERIFY_BOOK) {
      return CliRejectionPayloadMapper.attestationBookVerificationRejectedEnvelope(failure);
    }
    if (surface == OperationId.ATTESTATION_REVIEW) {
      return CliRejectionPayloadMapper.attestationReviewVerificationRejectedEnvelope(failure);
    }
    if (surface == OperationId.EXPORT_ATTESTATION_RECEIPT) {
      return CliRejectionPayloadMapper.attestationReceiptExportVerificationRejectedEnvelope(
          failure);
    }
    if (surface == OperationId.VERIFY_RECEIPT) {
      return CliRejectionPayloadMapper.attestationReceiptVerificationRejectedEnvelope(failure);
    }
    throw new AssertionError("Unexpected verification diagnostic surface: " + surface);
  }

  private static CliEnvelopeJsonModels.Envelope<?> admissionEnvelope(
      AttestationVerificationFailure failure, AdmissionContext context) {
    return switch (context) {
      case ORDINARY_LIVE_ADMISSION ->
          CliRejectionPayloadMapper.attestationAuthorizationRejectedEnvelope(failure);
      case REGISTRY_MUTATION ->
          CliRejectionPayloadMapper.attestationRegistryMutationRejectedEnvelope(failure);
      case BACKUP_ACKNOWLEDGEMENT ->
          CliRejectionPayloadMapper.backupAcknowledgementAuthorizationRejectedEnvelope(
              backupAcknowledgementAuthorizationRejected(failure));
    };
  }

  private static BackupBookResult.AcknowledgementAuthorizationRejected
      backupAcknowledgementAuthorizationRejected(AttestationVerificationFailure failure) {
    Path bookFile = Path.of("books", "current.sqlite");
    Path backupFile = Path.of("backups", "current.sqlite");
    Path backupKeyFile = Path.of("backups", "current.book-key");
    return new BackupBookResult.AcknowledgementAuthorizationRejected(
        bookFile,
        backupFile,
        backupKeyFile,
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        ProtectedBookPairPublicationCompletion.PUBLISHED,
        CliFixtureSupport.pairPublicationRetention(backupFile, backupKeyFile),
        failure);
  }
}
