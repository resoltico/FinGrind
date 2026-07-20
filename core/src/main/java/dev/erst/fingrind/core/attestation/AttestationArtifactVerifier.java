package dev.erst.fingrind.core.attestation;

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
    try {
      AttestationAuthorization.requireAuthorized(
          verification.registry(),
          AttestationAuthorizationContext.manifest(payload),
          artifact.manifest().authorizationEnvelope());
    } catch (AttestationAuthorizationException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw manifestFailure();
    }
    return new AttestationArtifactVerification(artifact, verification);
  }

  static AttestationReceiptVerification verifyReceipt(
      byte[] receiptBytes,
      AttestationBookVerification verification,
      boolean retainedWithinTrustBoundary) {
    AttestationDecodedEnvelope<AttestationReceiptPayload> receipt =
        AttestationDecodedEnvelope.receipt(receiptBytes);
    AttestationReceiptPayload payload = receipt.payload();
    AttestationBookVerification checkedVerification =
        Objects.requireNonNull(verification, "verification");
    try {
      if (!payload.bookId().equals(checkedVerification.bookId())
          || payload.operationOrder().compareTo(checkedVerification.headOrder()) > 0
          || !payload
              .operationHead()
              .equals(checkedVerification.headAt(payload.operationOrder()))) {
        throw receiptFailure();
      }
      AttestationAuthorization.requireAuthorized(
          checkedVerification.registry(),
          AttestationAuthorizationContext.receipt(payload),
          receipt.authorizationEnvelope());
      return new AttestationReceiptVerification(receipt, retainedWithinTrustBoundary);
    } catch (AttestationAuthorizationException exception) {
      if (exception.failure() == AttestationAuthorizationFailure.PREIMAGE_INVALID) {
        throw receiptFailure();
      }
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw receiptFailure();
    }
  }

  private static void requireManifestBinding(
      AttestationDecodedArtifact artifact,
      AttestationBookVerification verification,
      AttestationBackupManifestPayload payload) {
    try {
      if (!payload.snapshotDigest().equals(AttestationHash.sha256(artifact.snapshot()))
          || !payload.bookId().equals(verification.bookId())
          || payload.sourceOrder().compareTo(verification.headOrder()) > 0
          || !payload.sourceOperationHead().equals(verification.headAt(payload.sourceOrder()))) {
        throw manifestFailure();
      }
    } catch (AttestationAuthorizationException exception) {
      throw manifestFailure();
    } catch (IllegalArgumentException exception) {
      throw manifestFailure();
    }
  }

  private static AttestationBookVerification verifySnapshot(
      AttestationDecodedArtifact artifact, AttestationSnapshotDecoder snapshotDecoder) {
    try {
      AttestationBook decodedSnapshot =
          Objects.requireNonNull(snapshotDecoder, "snapshotDecoder").decode(artifact.snapshot());
      return AttestationBookVerifier.verify(decodedSnapshot);
    } catch (AttestationAuthorizationException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw manifestFailure();
    }
  }

  private static AttestationAuthorizationException manifestFailure() {
    return new AttestationAuthorizationException(AttestationAuthorizationFailure.MANIFEST_INVALID);
  }

  private static AttestationAuthorizationException receiptFailure() {
    return new AttestationAuthorizationException(AttestationAuthorizationFailure.RECEIPT_INVALID);
  }
}
