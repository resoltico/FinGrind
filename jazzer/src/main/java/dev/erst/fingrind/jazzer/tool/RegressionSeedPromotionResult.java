package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.Objects;

/** Describes the committed artifacts created by one seed-promotion command. */
public record RegressionSeedPromotionResult(
    String targetKey,
    Path sourceInputPath,
    Path committedInputPath,
    Path metadataPath,
    String coverageIntent,
    ReplayExpectation expectation) {
  public RegressionSeedPromotionResult {
    targetKey = ReplayModelValidation.requireText(targetKey, "targetKey");
    sourceInputPath = requireNormalizedPath(sourceInputPath, "sourceInputPath");
    committedInputPath = requireNormalizedPath(committedInputPath, "committedInputPath");
    metadataPath = requireNormalizedPath(metadataPath, "metadataPath");
    coverageIntent = ReplayModelValidation.requireText(coverageIntent, "coverageIntent");
    Objects.requireNonNull(expectation, "expectation must not be null");
  }

  private static Path requireNormalizedPath(Path value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    return value.toAbsolutePath().normalize();
  }
}
