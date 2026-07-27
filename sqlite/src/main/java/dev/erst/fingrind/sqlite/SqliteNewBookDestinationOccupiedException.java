package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Signals that exact private creation could not claim an exclusive new-book destination. */
final class SqliteNewBookDestinationOccupiedException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final Path targetPath;

  SqliteNewBookDestinationOccupiedException(Path targetPath, Throwable cause) {
    super(
        "The selected new FinGrind book destination is already occupied: "
            + Objects.requireNonNull(targetPath, "targetPath"),
        Objects.requireNonNull(cause, "cause"));
    this.targetPath = targetPath;
  }

  Path targetPath() {
    return targetPath;
  }
}
