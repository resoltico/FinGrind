package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Closed outcome family for exporting one no-clobber, quorum-signed attestation receipt. */
public sealed interface ExportAttestationReceiptResult
    permits ExportAttestationReceiptResult.Exported,
        ExportAttestationReceiptResult.AuthorizationRejected,
        ExportAttestationReceiptResult.VerificationRejected {

  /**
   * Successfully published one independently verifiable receipt artifact at its resolved canonical
   * physical location.
   */
  record Exported(
      ArtifactPublicationResult publication,
      UUID bookId,
      BigInteger operationOrder,
      String operationHeadHex,
      List<String> warnings)
      implements ExportAttestationReceiptResult {
    /** Validates the published receipt identity and its no-clobber publication warnings. */
    public Exported {
      Objects.requireNonNull(publication, "publication");
      Objects.requireNonNull(bookId, "bookId");
      Objects.requireNonNull(operationOrder, "operationOrder");
      if (operationOrder.signum() < 0 || operationOrder.bitLength() > Long.SIZE) {
        throw new IllegalArgumentException("operationOrder must be an unsigned 64-bit value.");
      }
      operationHeadHex = requireOperationHeadHex(operationHeadHex);
      warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    /** Returns the canonical physical receipt path created by the no-clobber publication. */
    public Path receiptFilePath() {
      return publication.publishedArtifactPath();
    }

    /** Returns the private receipt stage retained as immutable publication evidence. */
    public ArtifactPublicationRetention retainedStage() {
      return publication.retention();
    }
  }

  /** The live head's reconstructed attestation policy refused the selected receipt signers. */
  record AuthorizationRejected(AttestationVerificationFailure failure)
      implements ExportAttestationReceiptResult {
    /** Validates the exact live-admission authorization refusal. */
    public AuthorizationRejected {
      failure =
          AttestationVerificationFailure.requireAdmissionFailure(
              failure, AdmissionContext.ORDINARY_LIVE_ADMISSION);
    }
  }

  /** The source book's immutable attestation history could not be verified for receipt export. */
  record VerificationRejected(AttestationVerificationFailure failure)
      implements ExportAttestationReceiptResult {
    /** Validates the exact historical verification refusal. */
    public VerificationRejected {
      failure =
          AttestationVerificationFailure.requireVerificationFailure(
              failure, OperationId.EXPORT_ATTESTATION_RECEIPT);
    }
  }

  private static String requireOperationHeadHex(String operationHeadHex) {
    String value = Objects.requireNonNull(operationHeadHex, "operationHeadHex");
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "operationHeadHex must contain 64 lowercase hexadecimal characters.");
    }
    return value;
  }
}
