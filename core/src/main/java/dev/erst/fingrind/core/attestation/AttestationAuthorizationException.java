package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Signals the first deterministic authorization refusal at a resolving attestation position. */
public final class AttestationAuthorizationException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;
  private final AttestationAuthorizationFailure failure;

  AttestationAuthorizationException(AttestationAuthorizationFailure failure) {
    super(Objects.requireNonNull(failure, "failure").code());
    this.failure = failure;
  }

  AttestationAuthorizationException(AttestationAuthorizationFailure failure, Throwable cause) {
    super(
        Objects.requireNonNull(failure, "failure").code(), Objects.requireNonNull(cause, "cause"));
    this.failure = failure;
  }

  /** Returns the exact authorization invariant that refused the operation. */
  public AttestationAuthorizationFailure failure() {
    return failure;
  }
}
