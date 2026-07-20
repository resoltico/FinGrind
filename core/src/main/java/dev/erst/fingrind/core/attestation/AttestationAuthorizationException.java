package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Signals the first deterministic historical-authorization refusal. */
final class AttestationAuthorizationException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;
  private final AttestationAuthorizationFailure failure;

  AttestationAuthorizationException(AttestationAuthorizationFailure failure) {
    super(Objects.requireNonNull(failure, "failure").code());
    this.failure = failure;
  }

  AttestationAuthorizationFailure failure() {
    return failure;
  }
}
