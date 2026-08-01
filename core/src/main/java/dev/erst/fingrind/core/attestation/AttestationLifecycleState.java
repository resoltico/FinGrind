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
   * <p>Genesis establishes epoch one. A key epoch changes only with a verified {@code rekey-book}
   * operation, so the first rekey is epoch two; any gap, duplicate, or malformed historical rekey
   * evidence is structural invalidity rather than a value the next command may paper over. An empty
   * list represents no recorded rekeys and returns the first rekey epoch. Every nonempty list is
   * verified as one complete book chain before its lifecycle state is derived.
   */
  public static BigInteger nextKeyEpoch(List<AttestationEvidence> evidence) {
    List<AttestationEvidence> checkedEvidence =
        List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    if (!checkedEvidence.isEmpty()) {
      AttestationVerifier.verifyBook(checkedEvidence);
    }
    AttestationLifecycleHistory history = AttestationLifecycleHistory.genesis();
    for (AttestationEvidence operation : checkedEvidence) {
      AttestationEvidence checkedOperation =
          Objects.requireNonNull(operation, "evidence must not contain null");
      AttestationDecodedEnvelope<AttestationOperationPayload> envelope =
          AttestationDecodedEnvelope.operation(checkedOperation.operationEnvelope());
      AttestationOperationKind operationKind =
          AttestationOperationKind.forWireToken(envelope.payload().operationKind());
      if (operationKind != AttestationOperationKind.REKEY_BOOK) {
        continue;
      }
      AttestationPreimage request =
          AttestationPreimage.decode(
              checkedOperation.requestPreimage(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
      AttestationPreimage effect =
          AttestationPreimage.decode(
              checkedOperation.effectPreimage(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
      history = history.accept(operationKind, request, effect);
    }
    return history.nextRekeyEpoch();
  }
}
