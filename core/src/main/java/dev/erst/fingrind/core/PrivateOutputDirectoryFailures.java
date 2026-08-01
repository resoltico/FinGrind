package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Objects;

/** Builds the stable diagnostic failures for private output-directory admission. */
final class PrivateOutputDirectoryFailures {
  private PrivateOutputDirectoryFailures() {}

  static PrivateOutputDirectory.Violation requirement(Path directory, String requirement) {
    return new PrivateOutputDirectory.Violation(
        PrivateOutputDirectory.Violation.Kind.OWNER_ONLY_REQUIRED,
        "Output directory "
            + absolutePath(directory)
            + " "
            + Objects.requireNonNull(requirement, "requirement")
            + ".");
  }

  static PrivateOutputDirectory.Violation pathCollision(Path directory, String requirement) {
    return new PrivateOutputDirectory.Violation(
        PrivateOutputDirectory.Violation.Kind.PATH_COLLISION,
        "Output directory "
            + absolutePath(directory)
            + " "
            + Objects.requireNonNull(requirement, "requirement")
            + ".");
  }

  static PrivateOutputDirectory.Violation pathCollision(
      Path directory, String requirement, Throwable cause) {
    return new PrivateOutputDirectory.Violation(
        PrivateOutputDirectory.Violation.Kind.PATH_COLLISION,
        "Output directory "
            + absolutePath(directory)
            + " "
            + Objects.requireNonNull(requirement, "requirement")
            + ".",
        cause);
  }

  static String absolutePath(Path path) {
    return path.toAbsolutePath().normalize().toString();
  }
}
