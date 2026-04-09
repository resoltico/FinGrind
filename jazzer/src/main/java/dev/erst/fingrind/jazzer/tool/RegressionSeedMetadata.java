package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.Objects;

/** Describes the deterministic replay contract for one committed FinGrind Jazzer seed. */
public record RegressionSeedMetadata(String targetKey, String inputPath, ReplayExpectation expectation) {
  public RegressionSeedMetadata {
    targetKey = requireNonBlank(targetKey, "targetKey");
    inputPath = normalizeStoredPath(inputPath, "inputPath");
    expectation = Objects.requireNonNull(expectation, "expectation must not be null");
  }

  /** Resolves the committed input path against the supplied project directory. */
  Path inputPath(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return projectDirectory.toAbsolutePath().normalize().resolve(inputPath).normalize();
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    String normalized = value.strip();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }

  private static String normalizeStoredPath(String value, String fieldName) {
    String normalized = requireNonBlank(value, fieldName);
    Path storedPath = Path.of(normalized).normalize();
    if (storedPath.isAbsolute()) {
      throw new IllegalArgumentException(fieldName + " must be relative to the project directory");
    }
    String storedPathText = storedPath.toString();
    if (storedPathText.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not resolve to the project directory");
    }
    return storedPathText;
  }
}
