package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Covers the typed boundary that distinguishes a live admission refusal from a storage failure. */
class AttestationAdmissionRejectedExceptionTest {
  @Test
  void wrapsTheHistoricalAuthorizationFailureThatRefusedLiveAdmission() {
    AttestationAuthorizationException authorizationFailure =
        new AttestationAuthorizationException(AttestationAuthorizationFailure.KEY_NOT_ENROLLED);

    AttestationAdmissionRejectedException rejected =
        AttestationAdmissionRejectedException.from(authorizationFailure);

    assertEquals(AttestationAuthorizationFailure.KEY_NOT_ENROLLED, rejected.failure());
    assertEquals("attestation-key-not-enrolled", rejected.getMessage());
    assertSame(authorizationFailure, rejected.getCause());
  }

  @Test
  void preservesTheCandidateVerificationFailureThatExposedTheAuthorizationRefusal() {
    AttestationAuthorizationException authorizationFailure =
        new AttestationAuthorizationException(AttestationAuthorizationFailure.KEY_REVOKED);
    AttestationVerificationException candidateVerificationFailure =
        new AttestationVerificationException("attestation-key-revoked", authorizationFailure);

    AttestationAdmissionRejectedException rejected =
        AttestationAdmissionRejectedException.from(
            authorizationFailure, candidateVerificationFailure);

    assertEquals(AttestationAuthorizationFailure.KEY_REVOKED, rejected.failure());
    assertSame(candidateVerificationFailure, rejected.getCause());
  }

  @Test
  void reifiesAnAlreadyClassifiedBoundaryFailure() {
    AttestationAdmissionRejectedException rejected =
        AttestationAdmissionRejectedException.from(AttestationAuthorizationFailure.QUORUM_BELOW);

    assertEquals(AttestationAuthorizationFailure.QUORUM_BELOW, rejected.failure());
    assertEquals("attestation-quorum-below", rejected.getMessage());
    assertEquals(
        "attestation-quorum-below",
        assertInstanceOf(IllegalArgumentException.class, rejected.getCause()).getMessage());
  }
}
