package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Describes one committed-seed integrity defect discovered while auditing the regression floor. */
public record RegressionSeedIntegrityProblem(
    String targetKey,
    Path metadataPath,
    @Nullable Path inputPath,
    String problemKind,
    String message) {
  public RegressionSeedIntegrityProblem {
    targetKey = ReplayModelValidation.requireText(targetKey, "targetKey");
    metadataPath = requireNormalizedPath(metadataPath, "metadataPath");
    inputPath = inputPath == null ? null : requireNormalizedPath(inputPath, "inputPath");
    problemKind = ReplayModelValidation.requireText(problemKind, "problemKind");
    message = ReplayModelValidation.requireText(message, "message");
  }

  private static Path requireNormalizedPath(Path value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    return value.toAbsolutePath().normalize();
  }
}
