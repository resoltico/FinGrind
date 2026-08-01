package dev.erst.fingrind.core.attestation;

import java.nio.file.Path;
import java.util.Objects;

/** Reports a failure to use one explicitly selected encrypted attestation credential. */
public final class AttestationCredentialUseException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final transient Path credentialPath;
  private final String serializedCredentialPath;

  /** Captures the selected credential path without exposing secret material. */
  public AttestationCredentialUseException(Path credentialPath, String message, Exception cause) {
    super(message, Objects.requireNonNull(cause, "cause"));
    this.credentialPath = canonicalPath(credentialPath);
    this.serializedCredentialPath = this.credentialPath.toString();
  }

  /** Captures a selected credential that does not satisfy its declared public identity. */
  public AttestationCredentialUseException(Path credentialPath, String message) {
    super(message);
    this.credentialPath = canonicalPath(credentialPath);
    this.serializedCredentialPath = this.credentialPath.toString();
  }

  /** Returns the canonical selected encrypted credential file associated with the refusal. */
  public Path credentialPath() {
    return credentialPath == null ? Path.of(serializedCredentialPath) : credentialPath;
  }

  private static Path canonicalPath(Path credentialPath) {
    return Objects.requireNonNull(credentialPath, "credentialPath").toAbsolutePath().normalize();
  }
}
