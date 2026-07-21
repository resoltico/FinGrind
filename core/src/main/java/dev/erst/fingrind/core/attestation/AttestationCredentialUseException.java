package dev.erst.fingrind.core.attestation;

import java.nio.file.Path;
import java.util.Objects;

/** Reports a failure to use one explicitly selected encrypted attestation credential. */
public final class AttestationCredentialUseException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final Path credentialPath;

  /** Captures the selected credential path without exposing secret material. */
  public AttestationCredentialUseException(Path credentialPath, String message, Exception cause) {
    super(message, Objects.requireNonNull(cause, "cause"));
    this.credentialPath = Objects.requireNonNull(credentialPath, "credentialPath");
  }

  /** Captures a selected credential that does not satisfy its declared public identity. */
  public AttestationCredentialUseException(Path credentialPath, String message) {
    super(message);
    this.credentialPath = Objects.requireNonNull(credentialPath, "credentialPath");
  }

  /** Returns the selected encrypted credential file associated with the refusal. */
  public Path credentialPath() {
    return credentialPath;
  }
}
