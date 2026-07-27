package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Names the promotion destinations whose retained contents require operator inspection. */
public record RegressionSeedPromotionRetention(
    Path committedInputPath, Path metadataPath, List<Path> retainedArtifactPaths) {
  /** Normalizes all paths and requires retained artifacts to be promotion destinations. */
  public RegressionSeedPromotionRetention {
    committedInputPath = requireNormalizedPath(committedInputPath, "committedInputPath");
    metadataPath = requireNormalizedPath(metadataPath, "metadataPath");
    List<Path> candidatePaths =
        List.copyOf(
            Objects.requireNonNull(retainedArtifactPaths, "retainedArtifactPaths must not be null"));
    if (candidatePaths.isEmpty()) {
      throw new IllegalArgumentException("retainedArtifactPaths must not be empty.");
    }
    List<Path> normalizedRetainedArtifactPaths =
        candidatePaths.stream()
            .map(path -> requireNormalizedPath(path, "retainedArtifactPath"))
            .toList();
    if (normalizedRetainedArtifactPaths.stream().distinct().count()
        != normalizedRetainedArtifactPaths.size()) {
      throw new IllegalArgumentException("retainedArtifactPaths must not contain duplicates.");
    }
    for (Path normalizedRetainedArtifactPath : normalizedRetainedArtifactPaths) {
      if (!normalizedRetainedArtifactPath.equals(committedInputPath)
          && !normalizedRetainedArtifactPath.equals(metadataPath)) {
        throw new IllegalArgumentException(
            "retainedArtifactPaths must name only committedInputPath or metadataPath.");
      }
    }
    retainedArtifactPaths = List.copyOf(normalizedRetainedArtifactPaths);
  }

  private static Path requireNormalizedPath(Path path, String fieldName) {
    return Objects.requireNonNull(path, fieldName + " must not be null")
        .toAbsolutePath()
        .normalize();
  }
}
