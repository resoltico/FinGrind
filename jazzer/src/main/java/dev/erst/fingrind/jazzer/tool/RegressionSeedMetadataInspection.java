package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One metadata inspection outcome: either a valid entry or one integrity problem. */
record RegressionSeedMetadataInspection(
    @Nullable RegressionSeedCatalogEntry entry, @Nullable RegressionSeedIntegrityProblem problem) {
  static RegressionSeedMetadataInspection entry(RegressionSeedCatalogEntry entry) {
    return new RegressionSeedMetadataInspection(
        Objects.requireNonNull(entry, "entry must not be null"), null);
  }

  static RegressionSeedMetadataInspection problem(RegressionSeedIntegrityProblem problem) {
    return new RegressionSeedMetadataInspection(
        null, Objects.requireNonNull(problem, "problem must not be null"));
  }
}
