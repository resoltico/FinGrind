package dev.erst.fingrind.cli;

import java.nio.file.Path;
import java.util.Objects;

/** Captures an exception path as a stable lexical fact without filesystem re-admission. */
final class CliExceptionPathSnapshot {
  private CliExceptionPathSnapshot() {}

  static Path capture(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }

  static Path restore(String serializedPath) {
    return Path.of(Objects.requireNonNull(serializedPath, "serializedPath"));
  }
}
