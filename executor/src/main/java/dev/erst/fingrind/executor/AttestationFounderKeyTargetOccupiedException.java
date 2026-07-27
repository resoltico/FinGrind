package dev.erst.fingrind.executor;

import java.nio.file.Path;
import java.util.Objects;

/** Reports an admitted generated founder-key target that already exists and was not overwritten. */
public final class AttestationFounderKeyTargetOccupiedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient Path keyFilePath;
  private final String serializedKeyFilePath;

  /** Retains the canonical generated founder-key destination. */
  public AttestationFounderKeyTargetOccupiedException(Path keyFilePath, Exception cause) {
    super("The selected generated attestation founder-key target already exists.", cause);
    this.keyFilePath = canonicalPath(keyFilePath);
    this.serializedKeyFilePath = this.keyFilePath.toString();
  }

  /** Returns the canonical occupied generated founder-key target. */
  public Path keyFilePath() {
    return keyFilePath == null ? Path.of(serializedKeyFilePath) : keyFilePath;
  }

  private static Path canonicalPath(Path keyFilePath) {
    return Objects.requireNonNull(keyFilePath, "keyFilePath").toAbsolutePath().normalize();
  }
}
