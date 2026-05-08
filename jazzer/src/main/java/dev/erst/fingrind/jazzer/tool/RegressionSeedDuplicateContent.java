package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Describes one group of committed seeds that share identical input bytes. */
public record RegressionSeedDuplicateContent(String sha256, List<Path> inputPaths) {
  public RegressionSeedDuplicateContent {
    sha256 = ReplayModelValidation.requireText(sha256, "sha256");
    inputPaths = List.copyOf(Objects.requireNonNull(inputPaths, "inputPaths must not be null"));
    if (inputPaths.size() < 2) {
      throw new IllegalArgumentException(
          "Duplicate seed content groups must contain at least two input paths.");
    }
  }
}
