package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Summarizes the committed seeds owned by one replayable harness. */
public record RegressionSeedTargetAudit(
    String targetKey,
    int seedCount,
    List<RegressionSeedCatalogEntry> seeds,
    List<Path> orphanedInputs,
    List<RegressionSeedCatalogEntry> unexpectedFailureSeeds,
    List<RegressionSeedIntegrityProblem> integrityProblems) {
  public RegressionSeedTargetAudit {
    targetKey = ReplayModelValidation.requireText(targetKey, "targetKey");
    seedCount = ReplayModelValidation.requireNonNegative(seedCount, "seedCount");
    seeds = List.copyOf(Objects.requireNonNull(seeds, "seeds must not be null"));
    orphanedInputs =
        List.copyOf(Objects.requireNonNull(orphanedInputs, "orphanedInputs must not be null"));
    unexpectedFailureSeeds =
        List.copyOf(
            Objects.requireNonNull(
                unexpectedFailureSeeds, "unexpectedFailureSeeds must not be null"));
    integrityProblems =
        List.copyOf(
            Objects.requireNonNull(integrityProblems, "integrityProblems must not be null"));
    if (seedCount != seeds.size()) {
      throw new IllegalArgumentException("seedCount must match the number of supplied seeds.");
    }
  }
}
