package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** First deterministic failure while verifying persisted attestation evidence. */
public final class AttestationVerificationException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final String code;

  /** Creates one typed verification refusal with its stable public code. */
  public AttestationVerificationException(String code) {
    super(Objects.requireNonNull(code, "code"));
    this.code = code;
  }

  AttestationVerificationException(String code, Throwable cause) {
    super(Objects.requireNonNull(code, "code"), Objects.requireNonNull(cause, "cause"));
    this.code = code;
  }

  /** Returns the stable machine-readable refusal code. */
  public String code() {
    return code;
  }
}
