package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

/** Verifies independent manifest and receipt envelopes against a reconstructed immutable book. */
final class AttestationArtifactVerifier {
  private AttestationArtifactVerifier() {}

  static AttestationArtifactVerification verifyArtifact(
      byte[] artifactBytes, AttestationSnapshotDecoder snapshotDecoder) {
    AttestationDecodedArtifact artifact = AttestationDecodedArtifact.decode(artifactBytes);
    AttestationBookVerification verification = verifySnapshot(artifact, snapshotDecoder);
    AttestationBackupManifestPayload payload = artifact.manifest().payload();
    requireManifestBinding(artifact, verification, payload);
    AttestationAuthorization.requireAuthorized(
        verification.registry(),
        AttestationAuthorizationContext.manifest(payload),
        artifact.manifest().authorizationEnvelope());
    return new AttestationArtifactVerification(artifact, verification);
  }

  static AttestationReceiptVerification verifyReceipt(
      byte[] receiptBytes,
      AttestationBookVerification verification,
      AttestationReceiptRetention retention) {
    AttestationDecodedEnvelope<AttestationReceiptPayload> receipt =
        AttestationDecodedEnvelope.receipt(receiptBytes);
    AttestationReceiptPayload payload = receipt.payload();
    AttestationBookVerification checkedVerification =
        Objects.requireNonNull(verification, "verification");
    if (!payload.bookId().equals(checkedVerification.bookId())
        || payload.operationOrder().compareTo(checkedVerification.headOrder()) > 0
        || !payload.operationHead().equals(checkedVerification.headAt(payload.operationOrder()))) {
      throw receiptFailure();
    }
    AttestationAuthorization.requireAuthorized(
        checkedVerification.registry(),
        AttestationAuthorizationContext.receipt(payload),
        receipt.authorizationEnvelope());
    return new AttestationReceiptVerification(receipt, receiptFindings(retention));
  }

  private static void requireManifestBinding(
      AttestationDecodedArtifact artifact,
      AttestationBookVerification verification,
      AttestationBackupManifestPayload payload) {
    if (!payload.snapshotDigest().equals(AttestationHash.sha256(artifact.snapshot()))
        || !payload.bookId().equals(verification.bookId())
        || !payload.sourceOrder().equals(verification.headOrder())
        || !payload.sourceOperationHead().equals(verification.headAt(payload.sourceOrder()))) {
      throw manifestFailure();
    }
  }

  private static AttestationBookVerification verifySnapshot(
      AttestationDecodedArtifact artifact, AttestationSnapshotDecoder snapshotDecoder) {
    try {
      AttestationBook decodedSnapshot =
          Objects.requireNonNull(snapshotDecoder, "snapshotDecoder").decode(artifact.snapshot());
      return AttestationBookVerifier.verify(decodedSnapshot);
    } catch (RuntimeException exception) {
      throw AttestationFormatFailure.classify(
          exception, AttestationAuthorizationFailure.MANIFEST_INVALID);
    }
  }

  private static AttestationAuthorizationException manifestFailure() {
    return new AttestationAuthorizationException(AttestationAuthorizationFailure.MANIFEST_INVALID);
  }

  private static AttestationAuthorizationException receiptFailure() {
    return new AttestationAuthorizationException(AttestationAuthorizationFailure.RECEIPT_INVALID);
  }

  private static List<AttestationReceiptFinding> receiptFindings(
      AttestationReceiptRetention retention) {
    return switch (Objects.requireNonNull(retention, "retention")) {
      case INDEPENDENT -> List.of();
      case WITHIN_BOOK_TRUST_BOUNDARY -> List.of(AttestationReceiptFinding.NOT_INDEPENDENT);
    };
  }
}
