package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

/** Verifies independent manifest and receipt envelopes against a reconstructed immutable book. */
final class AttestationArtifactVerifier {
  private AttestationArtifactVerifier() {}

  static AttestationArtifactVerification verifyArtifact(
      byte[] artifactBytes, AttestationSnapshotDecoder snapshotDecoder) {
    AttestationDecodedArtifact artifact = AttestationDecodedArtifact.decode(artifactBytes);
    AttestationBackupManifestPayload payload = artifact.manifest().payload();
    requireSnapshotDigest(artifact, payload);
    AttestationBookVerification verification = verifySnapshot(artifact, snapshotDecoder);
    requireManifestChainBinding(verification, payload);
    AttestationAuthorization.requireAuthorized(
        verification.registry(),
        AttestationAuthorizationContext.manifest(payload),
        artifact.manifest().authorizationEnvelope());
    return new AttestationArtifactVerification(artifact, verification);
  }

  static AttestationBackupArtifactVerification verifyBackupArtifact(
      byte[] artifactBytes, AttestationArtifactSnapshotReader snapshotReader) {
    AttestationDecodedArtifact artifact = AttestationDecodedArtifact.decode(artifactBytes);
    AttestationBackupManifestPayload payload = artifact.manifest().payload();
    requireSnapshotDigest(artifact, payload);
    AttestationBookVerification verification = verifySnapshot(artifact, snapshotReader);
    requireManifestChainBinding(verification, payload);
    AttestationAuthorization.requireAuthorized(
        verification.registry(),
        AttestationAuthorizationContext.manifest(payload),
        artifact.manifest().authorizationEnvelope());
    return new AttestationBackupArtifactVerification(
        artifact.snapshot(),
        payload.bookId(),
        payload.backupId(),
        payload.sourceOrder(),
        payload.sourceOperationHead().bytes(),
        payload.snapshotDigest().bytes(),
        artifact.digest().bytes(),
        publicVerification(verification));
  }

  static AttestationReceiptVerification verifyReceipt(
      byte[] receiptBytes,
      AttestationBookVerification verification,
      AttestationReceiptRetention retention) {
    AttestationDecodedEnvelope<AttestationReceiptPayload> receipt = decodeReceipt(receiptBytes);
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

  static AttestationReceiptVerificationResult verifyReceiptArtifact(
      byte[] receiptBytes,
      List<AttestationEvidence> evidence,
      AttestationReceiptRetention retention) {
    AttestationBookVerification verification = verifyEvidence(evidence);
    AttestationReceiptVerification receipt = verifyReceipt(receiptBytes, verification, retention);
    AttestationReceiptPayload payload = receipt.receipt().payload();
    return new AttestationReceiptVerificationResult(
        payload.bookId(),
        payload.operationOrder(),
        payload.operationHead().bytes(),
        receipt.findings().stream().map(AttestationReceiptFinding::code).toList());
  }

  private static void requireSnapshotDigest(
      AttestationDecodedArtifact artifact, AttestationBackupManifestPayload payload) {
    if (!payload.snapshotDigest().equals(AttestationHash.sha256(artifact.snapshot()))) {
      throw manifestFailure();
    }
  }

  private static void requireManifestChainBinding(
      AttestationBookVerification verification, AttestationBackupManifestPayload payload) {
    if (!payload.bookId().equals(verification.bookId())
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
    } catch (AttestationAuthorizationException exception) {
      if (exception.failure() == AttestationAuthorizationFailure.UNSUPPORTED_VERSION) {
        throw exception;
      }
      throw manifestFailure(exception);
    } catch (RuntimeException exception) {
      throw manifestFailure(exception);
    }
  }

  private static AttestationBookVerification verifySnapshot(
      AttestationDecodedArtifact artifact, AttestationArtifactSnapshotReader snapshotReader) {
    try {
      return verifyEvidence(
          Objects.requireNonNull(snapshotReader, "snapshotReader").read(artifact.snapshot()));
    } catch (AttestationAuthorizationException exception) {
      if (exception.failure() == AttestationAuthorizationFailure.UNSUPPORTED_VERSION) {
        throw exception;
      }
      throw manifestFailure(exception);
    } catch (RuntimeException exception) {
      throw manifestFailure(exception);
    }
  }

  private static AttestationBookVerification verifyEvidence(List<AttestationEvidence> evidence) {
    List<AttestationEvidence> checkedEvidence =
        List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    if (checkedEvidence.isEmpty()) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.PREIMAGE_INVALID);
    }
    return AttestationBookVerifier.verify(
        new AttestationBook(
            checkedEvidence.stream()
                .map(
                    operation -> {
                      AttestationEvidence checkedOperation =
                          Objects.requireNonNull(operation, "evidence must not contain null");
                      return AttestationBookOperation.decode(
                          checkedOperation.operationEnvelope(),
                          checkedOperation.requestPreimage(),
                          checkedOperation.effectPreimage());
                    })
                .toList()));
  }

  private static AttestationDecodedEnvelope<AttestationReceiptPayload> decodeReceipt(
      byte[] receiptBytes) {
    try {
      return AttestationDecodedEnvelope.receipt(receiptBytes);
    } catch (AttestationAuthorizationException exception) {
      if (exception.failure() == AttestationAuthorizationFailure.UNSUPPORTED_VERSION) {
        throw exception;
      }
      throw new AttestationReceiptArtifactException(exception);
    }
  }

  private static AttestationVerification publicVerification(
      AttestationBookVerification verification) {
    return new AttestationVerification(
        verification.bookId(), verification.headOrder(), verification.head().bytes(), List.of());
  }

  private static AttestationAuthorizationException manifestFailure() {
    return new AttestationAuthorizationException(AttestationAuthorizationFailure.MANIFEST_INVALID);
  }

  private static AttestationAuthorizationException manifestFailure(Throwable cause) {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.MANIFEST_INVALID, cause);
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
