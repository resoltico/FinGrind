package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Signals an exact authorization refusal while admitting a newly signed live-book operation. */
public final class AttestationAdmissionRejectedException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final AttestationAuthorizationFailure failure;

  private AttestationAdmissionRejectedException(
      AttestationAuthorizationFailure failure, Throwable cause) {
    super(
        Objects.requireNonNull(failure, "failure").code(), Objects.requireNonNull(cause, "cause"));
    this.failure = failure;
  }

  /** Reclassifies one authorization failure at the live mutation-admission boundary. */
  public static AttestationAdmissionRejectedException from(
      AttestationAuthorizationException exception) {
    return from(exception, exception);
  }

  /**
   * Reclassifies one authorization failure while retaining the candidate-verification evidence that
   * exposed it.
   */
  public static AttestationAdmissionRejectedException from(
      AttestationAuthorizationException exception, Throwable cause) {
    AttestationAuthorizationException checked = Objects.requireNonNull(exception, "exception");
    return new AttestationAdmissionRejectedException(
        checked.failure(), Objects.requireNonNull(cause, "cause"));
  }

  /** Reifies one already-classified authorization refusal at an application boundary. */
  public static AttestationAdmissionRejectedException from(
      AttestationAuthorizationFailure failure) {
    AttestationAuthorizationFailure checked = Objects.requireNonNull(failure, "failure");
    return new AttestationAdmissionRejectedException(
        checked, new IllegalArgumentException(checked.code()));
  }

  /** Returns the exact historical authorization invariant that refused admission. */
  public AttestationAuthorizationFailure failure() {
    return failure;
  }
}
