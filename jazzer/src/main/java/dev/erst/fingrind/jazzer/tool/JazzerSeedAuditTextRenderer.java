package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.List;

/** Renders committed-seed audit reports for text-mode Jazzer CLI output. */
final class JazzerSeedAuditTextRenderer {
  private JazzerSeedAuditTextRenderer() {}

  static String render(RegressionSeedAuditReport report) {
    StringBuilder builder = new StringBuilder(512);
    appendSummary(builder, report);
    report.targets().forEach(target -> appendTargetAudit(builder, target));
    appendReportOrphanedInputs(builder, report.orphanedInputPaths());
    appendReportUnexpectedFailures(builder, report.unexpectedFailureSeeds());
    appendDuplicateGroups(builder, report.duplicateContentGroups());
    appendReportIntegrityProblems(builder, report.integrityProblems());
    return builder.toString();
  }

  private static void appendSummary(StringBuilder builder, RegressionSeedAuditReport report) {
    builder
        .append("Committed seed audit")
        .append(System.lineSeparator())
        .append("Total seeds: ")
        .append(report.totalSeedCount())
        .append(System.lineSeparator())
        .append("Unique input bodies: ")
        .append(report.uniqueInputContentCount())
        .append(System.lineSeparator())
        .append("Orphaned input files: ")
        .append(report.orphanedInputCount())
        .append(System.lineSeparator())
        .append("Unexpected-failure expectations: ")
        .append(report.unexpectedFailureSeedCount())
        .append(System.lineSeparator())
        .append("Integrity problems: ")
        .append(report.integrityProblemCount())
        .append(System.lineSeparator())
        .append("Duplicate content groups: ")
        .append(report.duplicateContentGroups().size());
  }

  private static void appendTargetAudit(StringBuilder builder, RegressionSeedTargetAudit target) {
    builder
        .append(System.lineSeparator())
        .append(System.lineSeparator())
        .append("Target: ")
        .append(target.targetKey())
        .append(" (")
        .append(target.seedCount())
        .append(')');
    target.seeds().forEach(seed -> appendTargetSeed(builder, seed));
    appendTargetOrphanedInputs(builder, target.orphanedInputs());
    appendTargetUnexpectedFailures(builder, target.unexpectedFailureSeeds());
    appendTargetIntegrityProblems(builder, target.integrityProblems());
  }

  private static void appendTargetSeed(StringBuilder builder, RegressionSeedCatalogEntry seed) {
    builder
        .append(System.lineSeparator())
        .append("  ")
        .append(seed.inputPath().getFileName())
        .append(" | ")
        .append(seed.expectation().outcomeKind().wireValue())
        .append(" | ")
        .append(seed.coverageIntent());
  }

  private static void appendTargetOrphanedInputs(StringBuilder builder, List<Path> orphanedInputs) {
    if (orphanedInputs.isEmpty()) {
      return;
    }
    builder.append(System.lineSeparator()).append("  Orphaned inputs:");
    orphanedInputs.forEach(
        orphanedInput ->
            builder
                .append(System.lineSeparator())
                .append("    ")
                .append(orphanedInput.getFileName()));
  }

  private static void appendTargetUnexpectedFailures(
      StringBuilder builder, List<RegressionSeedCatalogEntry> unexpectedFailureSeeds) {
    if (unexpectedFailureSeeds.isEmpty()) {
      return;
    }
    builder.append(System.lineSeparator()).append("  Unexpected-failure expectations:");
    unexpectedFailureSeeds.forEach(
        unexpectedFailureSeed ->
            builder
                .append(System.lineSeparator())
                .append("    ")
                .append(unexpectedFailureSeed.inputPath().getFileName())
                .append(" | ")
                .append(unexpectedFailureSeed.expectation().message()));
  }

  private static void appendTargetIntegrityProblems(
      StringBuilder builder, List<RegressionSeedIntegrityProblem> integrityProblems) {
    if (integrityProblems.isEmpty()) {
      return;
    }
    builder.append(System.lineSeparator()).append("  Integrity problems:");
    integrityProblems.forEach(
        integrityProblem ->
            builder
                .append(System.lineSeparator())
                .append("    ")
                .append(integrityProblem.problemKind())
                .append(" | ")
                .append(integrityProblem.message()));
  }

  private static void appendReportOrphanedInputs(StringBuilder builder, List<Path> orphanedInputs) {
    if (orphanedInputs.isEmpty()) {
      return;
    }
    builder
        .append(System.lineSeparator())
        .append(System.lineSeparator())
        .append("Orphaned inputs:");
    orphanedInputs.forEach(
        orphanedInput -> builder.append(System.lineSeparator()).append("  ").append(orphanedInput));
  }

  private static void appendReportUnexpectedFailures(
      StringBuilder builder, List<RegressionSeedCatalogEntry> unexpectedFailureSeeds) {
    if (unexpectedFailureSeeds.isEmpty()) {
      return;
    }
    builder
        .append(System.lineSeparator())
        .append(System.lineSeparator())
        .append("Unexpected-failure expectations:");
    unexpectedFailureSeeds.forEach(
        unexpectedFailureSeed ->
            builder
                .append(System.lineSeparator())
                .append("  ")
                .append(unexpectedFailureSeed.metadataPath())
                .append(" -> ")
                .append(unexpectedFailureSeed.expectation().message()));
  }

  private static void appendDuplicateGroups(
      StringBuilder builder, List<RegressionSeedDuplicateContent> duplicateGroups) {
    if (duplicateGroups.isEmpty()) {
      return;
    }
    builder.append(System.lineSeparator()).append(System.lineSeparator()).append("Duplicates:");
    duplicateGroups.forEach(duplicateGroup -> appendDuplicateGroup(builder, duplicateGroup));
  }

  private static void appendDuplicateGroup(
      StringBuilder builder, RegressionSeedDuplicateContent duplicateGroup) {
    builder
        .append(System.lineSeparator())
        .append("  sha256=")
        .append(duplicateGroup.sha256())
        .append(" count=")
        .append(duplicateGroup.inputPaths().size());
    duplicateGroup
        .inputPaths()
        .forEach(
            inputPath -> builder.append(System.lineSeparator()).append("    ").append(inputPath));
  }

  private static void appendReportIntegrityProblems(
      StringBuilder builder, List<RegressionSeedIntegrityProblem> integrityProblems) {
    if (integrityProblems.isEmpty()) {
      return;
    }
    builder
        .append(System.lineSeparator())
        .append(System.lineSeparator())
        .append("Integrity problems:");
    integrityProblems.forEach(
        integrityProblem -> appendIntegrityProblem(builder, integrityProblem));
  }

  private static void appendIntegrityProblem(
      StringBuilder builder, RegressionSeedIntegrityProblem integrityProblem) {
    builder
        .append(System.lineSeparator())
        .append("  ")
        .append(integrityProblem.problemKind())
        .append(" | ")
        .append(integrityProblem.metadataPath());
    if (integrityProblem.inputPath() != null) {
      builder
          .append(System.lineSeparator())
          .append("    input: ")
          .append(integrityProblem.inputPath());
    }
    builder.append(System.lineSeparator()).append("    ").append(integrityProblem.message());
  }
}
