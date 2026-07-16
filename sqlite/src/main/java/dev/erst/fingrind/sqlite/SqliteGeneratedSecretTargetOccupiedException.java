package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Signals that a generated-secret target became occupied before it could be published. */
final class SqliteGeneratedSecretTargetOccupiedException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final Path targetPath;

  SqliteGeneratedSecretTargetOccupiedException(Path targetPath) {
    super(
        "Generated secret target is occupied: " + Objects.requireNonNull(targetPath, "targetPath"));
    this.targetPath = targetPath;
  }

  SqliteGeneratedSecretTargetOccupiedException(Path targetPath, Throwable cause) {
    super(
        "Generated secret target is occupied: " + Objects.requireNonNull(targetPath, "targetPath"),
        cause);
    this.targetPath = targetPath;
  }

  Path targetPath() {
    return targetPath;
  }
}
