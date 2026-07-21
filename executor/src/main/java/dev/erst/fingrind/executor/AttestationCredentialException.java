package dev.erst.fingrind.executor;

import java.nio.file.Path;
import java.util.Objects;

/** Typed operational refusal while opening a declared encrypted attestation credential source. */
public final class AttestationCredentialException extends RuntimeException {
  private final Path credentialPath;

  /** Captures the selected credential path without exposing secret content. */
  public AttestationCredentialException(Path credentialPath, Exception cause) {
    super(
        "Attestation credential source could not be opened.",
        Objects.requireNonNull(cause, "cause"));
    this.credentialPath = Objects.requireNonNull(credentialPath, "credentialPath");
  }

  /** Returns the credential file path associated with the refusal. */
  public Path credentialPath() {
    return credentialPath;
  }
}
