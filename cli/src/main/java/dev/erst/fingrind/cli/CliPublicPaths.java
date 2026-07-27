package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import java.util.Objects;

/** Shared path policy for CLI transport surfaces. */
final class CliPublicPaths {
  private CliPublicPaths() {}

  static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(Objects.requireNonNull(path, "path"));
  }

  static String redactedValue(Path path) {
    return hint(path).value();
  }

  static String redactedValue(PublicPathHint pathHint) {
    return Objects.requireNonNull(pathHint, "pathHint").value();
  }

  /** Returns a normalized absolute path string for machine-readable CLI output. */
  static String absoluteValue(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
  }
}
