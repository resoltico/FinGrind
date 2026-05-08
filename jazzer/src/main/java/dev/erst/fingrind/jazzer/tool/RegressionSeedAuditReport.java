package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Summarizes the committed Jazzer seed floor and any duplicate-content defects. */
public record RegressionSeedAuditReport(
    int totalSeedCount,
    int uniqueInputContentCount,
    int orphanedInputCount,
    int unexpectedFailureSeedCount,
    int integrityProblemCount,
    List<RegressionSeedTargetAudit> targets,
    List<RegressionSeedDuplicateContent> duplicateContentGroups,
    List<Path> orphanedInputPaths,
    List<RegressionSeedCatalogEntry> unexpectedFailureSeeds,
    List<RegressionSeedIntegrityProblem> integrityProblems) {
  public RegressionSeedAuditReport {
    totalSeedCount = ReplayModelValidation.requireNonNegative(totalSeedCount, "totalSeedCount");
    uniqueInputContentCount =
        ReplayModelValidation.requireNonNegative(
            uniqueInputContentCount, "uniqueInputContentCount");
    orphanedInputCount =
        ReplayModelValidation.requireNonNegative(orphanedInputCount, "orphanedInputCount");
    unexpectedFailureSeedCount =
        ReplayModelValidation.requireNonNegative(
            unexpectedFailureSeedCount, "unexpectedFailureSeedCount");
    integrityProblemCount =
        ReplayModelValidation.requireNonNegative(integrityProblemCount, "integrityProblemCount");
    targets = List.copyOf(Objects.requireNonNull(targets, "targets must not be null"));
    duplicateContentGroups =
        List.copyOf(
            Objects.requireNonNull(
                duplicateContentGroups, "duplicateContentGroups must not be null"));
    orphanedInputPaths =
        List.copyOf(
            Objects.requireNonNull(orphanedInputPaths, "orphanedInputPaths must not be null"));
    unexpectedFailureSeeds =
        List.copyOf(
            Objects.requireNonNull(
                unexpectedFailureSeeds, "unexpectedFailureSeeds must not be null"));
    integrityProblems =
        List.copyOf(
            Objects.requireNonNull(integrityProblems, "integrityProblems must not be null"));
    if (uniqueInputContentCount > totalSeedCount) {
      throw new IllegalArgumentException("uniqueInputContentCount must not exceed totalSeedCount.");
    }
    if (orphanedInputCount != orphanedInputPaths.size()) {
      throw new IllegalArgumentException(
          "orphanedInputCount must match the number of supplied orphaned input paths.");
    }
    if (unexpectedFailureSeedCount != unexpectedFailureSeeds.size()) {
      throw new IllegalArgumentException(
          "unexpectedFailureSeedCount must match the number of supplied unexpected-failure seeds.");
    }
    if (integrityProblemCount != integrityProblems.size()) {
      throw new IllegalArgumentException(
          "integrityProblemCount must match the number of supplied integrity problems.");
    }
  }
}
