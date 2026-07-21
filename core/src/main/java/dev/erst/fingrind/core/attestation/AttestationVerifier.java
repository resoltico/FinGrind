package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

/** Public, pure boundary for verification of persisted operation evidence. */
public final class AttestationVerifier {
  private AttestationVerifier() {}

  /**
   * Verifies one complete operation chain from genesis and returns its authenticated head.
   *
   * @throws AttestationVerificationException when the first canonical attestation rule fails
   */
  public static AttestationVerification verifyBook(List<AttestationEvidence> operations) {
    Objects.requireNonNull(operations, "operations");
    if (operations.isEmpty()) {
      throw new AttestationVerificationException("attestation-preimage-invalid");
    }
    try {
      AttestationBookVerification verification =
          AttestationBookVerifier.verify(
              new AttestationBook(
                  operations.stream()
                      .map(
                          operation -> {
                            AttestationEvidence evidence =
                                Objects.requireNonNull(
                                    operation, "operations must not contain null");
                            return AttestationBookOperation.decode(
                                evidence.operationEnvelope(),
                                evidence.requestPreimage(),
                                evidence.effectPreimage());
                          })
                      .toList()));
      return new AttestationVerification(
          verification.bookId(), verification.headOrder(), verification.head().bytes(), List.of());
    } catch (AttestationAuthorizationException exception) {
      throw new AttestationVerificationException(exception.failure().code(), exception);
    }
  }
}
