package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.Objects;

/** Captures one committed seed together with its metadata and normalized file paths. */
public record RegressionSeedCatalogEntry(
    String targetKey,
    Path metadataPath,
    Path inputPath,
    String coverageIntent,
    ReplayExpectation expectation,
    String sha256) {
  public RegressionSeedCatalogEntry {
    targetKey = ReplayModelValidation.requireText(targetKey, "targetKey");
    metadataPath = requireNormalizedPath(metadataPath, "metadataPath");
    inputPath = requireNormalizedPath(inputPath, "inputPath");
    coverageIntent = ReplayModelValidation.requireText(coverageIntent, "coverageIntent");
    Objects.requireNonNull(expectation, "expectation must not be null");
    sha256 = ReplayModelValidation.requireText(sha256, "sha256");
  }

  private static Path requireNormalizedPath(Path value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    return value.toAbsolutePath().normalize();
  }
}
