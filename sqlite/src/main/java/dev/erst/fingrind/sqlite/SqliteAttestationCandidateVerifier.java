package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationException;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import java.util.List;
import java.util.Objects;

/**
 * Verifies one newly signed candidate and preserves exact live-admission authorization refusals.
 */
final class SqliteAttestationCandidateVerifier {
  private SqliteAttestationCandidateVerifier() {}

  static AttestationVerification verify(List<AttestationEvidence> evidence) {
    try {
      return AttestationVerifier.verifyBook(evidence);
    } catch (AttestationVerificationException exception) {
      throw AttestationAdmissionRejectedException.from(
          (AttestationAuthorizationException)
              Objects.requireNonNull(exception.getCause(), "verification exception cause"),
          exception);
    }
  }
}
