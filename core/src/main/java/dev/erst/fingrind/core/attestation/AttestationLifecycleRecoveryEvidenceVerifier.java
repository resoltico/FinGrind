package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Verifies that a recovered protected-book lifecycle head is the exact immutable operation that its
 * durable pair-publication record names.
 *
 * <p>A recovery record is filesystem evidence, not cryptographic authority. The signed head must
 * independently prove the restore acknowledgement or rekey predecessor that the record claims.
 */
public final class AttestationLifecycleRecoveryEvidenceVerifier {
  private AttestationLifecycleRecoveryEvidenceVerifier() {}

  /** Returns whether the verified head is the exact restore operation for an acknowledgement. */
  public static boolean matchesRestoreHead(
      List<AttestationEvidence> evidence,
      AttestationBackupAcknowledgement acknowledgement,
      BigInteger expectedOrder,
      byte[] expectedHead) {
    try {
      AttestationBookVerification verification = verify(evidence);
      AttestationBookVerification.VerifiedOperation operation =
          exactHead(verification, expectedOrder, expectedHead);
      return operationKindIs(operation, AttestationOperationKind.RESTORE_BOOK)
          && matchesRestoreAcknowledgement(
              operation, Objects.requireNonNull(acknowledgement, "acknowledgement"));
    } catch (RuntimeException invalid) {
      return false;
    }
  }

  /** Returns whether the verified head is a rekey that continues from the named source head. */
  public static boolean matchesRekeyHead(
      List<AttestationEvidence> evidence,
      BigInteger sourceOrder,
      byte[] sourceHead,
      BigInteger expectedOrder,
      byte[] expectedHead) {
    try {
      BigInteger checkedSourceOrder = Objects.requireNonNull(sourceOrder, "sourceOrder");
      byte[] checkedSourceHead = checkedHash(sourceHead, "sourceHead");
      AttestationBookVerification verification = verify(evidence);
      AttestationBookVerification.VerifiedOperation operation =
          exactHead(verification, expectedOrder, expectedHead);
      return operationKindIs(operation, AttestationOperationKind.REKEY_BOOK)
          && checkedSourceOrder.add(BigInteger.ONE).equals(operation.operationOrder())
          && Arrays.equals(
              operation.operation().envelope().payload().previousHead().bytes(), checkedSourceHead);
    } catch (RuntimeException invalid) {
      return false;
    }
  }

  private static AttestationBookVerification verify(List<AttestationEvidence> evidence) {
    return AttestationVerifier.verifyEvidence(
        List.copyOf(Objects.requireNonNull(evidence, "evidence")), List.of());
  }

  private static AttestationBookVerification.VerifiedOperation exactHead(
      AttestationBookVerification verification, BigInteger expectedOrder, byte[] expectedHead) {
    BigInteger checkedExpectedOrder = Objects.requireNonNull(expectedOrder, "expectedOrder");
    byte[] checkedExpectedHead = checkedHash(expectedHead, "expectedHead");
    if (!verification.headOrder().equals(checkedExpectedOrder)
        || !Arrays.equals(verification.head().bytes(), checkedExpectedHead)) {
      throw new IllegalArgumentException(
          "Recovered lifecycle head does not match durable evidence.");
    }
    return verification.operationAt(checkedExpectedOrder);
  }

  private static boolean operationKindIs(
      AttestationBookVerification.VerifiedOperation operation, AttestationOperationKind expected) {
    return AttestationOperationKind.forWireToken(
            operation.operation().envelope().payload().operationKind())
        == expected;
  }

  private static boolean matchesRestoreAcknowledgement(
      AttestationBookVerification.VerifiedOperation operation,
      AttestationBackupAcknowledgement acknowledgement) {
    AttestationPreimage.Fact restore =
        AttestationPreimageFields.records(operation.operation().requestPreimage(), 0x0160)
            .getFirst();
    AttestationBackupAcknowledgement recorded =
        new AttestationBackupAcknowledgement(
            AttestationPreimageValueReader.uuid(
                restore, 0, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
            AttestationPreimageValueReader.hash(
                    restore, 1, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)
                .bytes(),
            AttestationPreimageValueReader.unsigned64(
                restore, 2, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
            AttestationPreimageValueReader.hash(
                    restore, 3, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)
                .bytes());
    return recorded.sameTuple(acknowledgement);
  }

  private static byte[] checkedHash(byte[] hash, String name) {
    byte[] copy = Objects.requireNonNull(hash, name).clone();
    if (copy.length != 32) {
      throw new IllegalArgumentException(name + " must contain exactly 32 bytes.");
    }
    return copy;
  }
}
