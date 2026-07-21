package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Derives immutable lifecycle state needed to admit the next lifecycle operation. */
public final class AttestationLifecycleState {
  private AttestationLifecycleState() {}

  /**
   * Returns the next sequential book-key epoch from verified immutable operation evidence.
   *
   * <p>A key epoch changes only with a verified {@code rekey-book} operation. The first rekey is
   * epoch one; any gap, duplicate, or malformed historical rekey evidence is structural invalidity
   * rather than a value the next command may paper over.
   */
  public static BigInteger nextKeyEpoch(List<AttestationEvidence> evidence) {
    List<AttestationEvidence> checkedEvidence =
        List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    BigInteger expectedEpoch = BigInteger.ONE;
    for (AttestationEvidence operation : checkedEvidence) {
      AttestationEvidence checkedOperation =
          Objects.requireNonNull(operation, "evidence must not contain null");
      AttestationDecodedEnvelope<AttestationOperationPayload> envelope =
          AttestationDecodedEnvelope.operation(checkedOperation.operationEnvelope());
      if (AttestationOperationKind.forWireToken(envelope.payload().operationKind())
          != AttestationOperationKind.REKEY_BOOK) {
        continue;
      }
      AttestationPreimage effect =
          AttestationPreimage.decode(
              checkedOperation.effectPreimage(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
      List<AttestationPreimage.Fact> records =
          effect.records().stream().filter(record -> record.recordTypeTag() == 0x0007).toList();
      if (records.size() != 1) {
        throw new AttestationVerificationException("attestation-preimage-invalid");
      }
      BigInteger epoch =
          AttestationPreimageValueReader.unsigned64(
              records.getFirst(), 1, AttestationAuthorizationFailure.PREIMAGE_INVALID);
      if (!epoch.equals(expectedEpoch)) {
        throw new AttestationVerificationException("attestation-preimage-invalid");
      }
      expectedEpoch = expectedEpoch.add(BigInteger.ONE);
    }
    return expectedEpoch;
  }
}
