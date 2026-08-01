package dev.erst.fingrind.executor;

import java.nio.file.Path;
import java.util.Objects;

/** Typed operational refusal while opening a declared encrypted attestation credential source. */
public final class AttestationCredentialException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient Path credentialPath;
  private final String serializedCredentialPath;

  /** Captures the selected credential path without exposing secret content. */
  public AttestationCredentialException(Path credentialPath, Exception cause) {
    super(
        "Attestation credential source could not be opened.",
        Objects.requireNonNull(cause, "cause"));
    this.credentialPath = canonicalPath(credentialPath);
    this.serializedCredentialPath = this.credentialPath.toString();
  }

  /** Returns the canonical credential file path associated with the refusal. */
  public Path credentialPath() {
    return credentialPath == null ? Path.of(serializedCredentialPath) : credentialPath;
  }

  private static Path canonicalPath(Path credentialPath) {
    return Objects.requireNonNull(credentialPath, "credentialPath").toAbsolutePath().normalize();
  }
}
