package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Signals that selected receipt bytes cannot be decoded as a receipt artifact. */
public final class AttestationReceiptArtifactException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  AttestationReceiptArtifactException(Throwable cause) {
    super("The selected receipt artifact is malformed.", Objects.requireNonNull(cause, "cause"));
  }
}
