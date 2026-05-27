package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Synthesizes committed regression-seed audits from valid entries and integrity findings. */
final class RegressionSeedAuditor {
  private RegressionSeedAuditor() {}

  static RegressionSeedAuditReport audit(Path projectDirectory) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return audit(projectDirectory, List.of(JazzerHarness.values()));
  }

  static RegressionSeedAuditReport audit(Path projectDirectory, JazzerHarness harness)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    return audit(projectDirectory, List.of(harness));
  }

  private static RegressionSeedAuditReport audit(
      Path projectDirectory, List<JazzerHarness> harnesses) throws IOException {
    List<RegressionSeedCatalogEntry> allEntries = new ArrayList<>();
    List<Path> orphanedInputPaths = new ArrayList<>();
    List<RegressionSeedCatalogEntry> unexpectedFailureSeeds = new ArrayList<>();
    List<RegressionSeedIntegrityProblem> integrityProblems = new ArrayList<>();
    List<RegressionSeedTargetAudit> targets = new ArrayList<>();
    Set<Path> scopedSeedInputPaths = new HashSet<>();
    for (JazzerHarness harness : harnesses) {
      HarnessAuditState harnessState = newHarnessAuditState();
      Path normalizedHarnessInputDirectory =
          harness.inputDirectory(projectDirectory).toAbsolutePath().normalize();
      for (Path metadataPath : RegressionSeedPaths.metadataPaths(projectDirectory, harness)) {
        RegressionSeedMetadataInspection inspection =
            RegressionSeedMetadataInspector.inspectMetadataPath(
                projectDirectory, harness, metadataPath);
        if (inspection.entry() != null) {
          harnessState.entries().add(inspection.entry());
          harnessState.referencedInputs().add(inspection.entry().inputPath());
          continue;
        }
        RegressionSeedIntegrityProblem integrityProblem =
            Objects.requireNonNull(inspection.problem(), "problem must not be null");
        harnessState.integrityProblems().add(integrityProblem);
        if (integrityProblem.inputPath() != null
            && integrityProblem.inputPath().startsWith(normalizedHarnessInputDirectory)) {
          harnessState.referencedInputs().add(integrityProblem.inputPath());
        }
      }
      List<Path> harnessOrphans =
          RegressionSeedPaths.inputPaths(projectDirectory, harness).stream()
              .map(path -> path.toAbsolutePath().normalize())
              .filter(path -> !harnessState.referencedInputs().contains(path))
              .sorted()
              .toList();
      List<RegressionSeedCatalogEntry> harnessUnexpectedFailureSeeds =
          harnessState.entries().stream()
              .filter(
                  entry ->
                      entry.expectation().outcomeKind() == ReplayOutcomeKind.UNEXPECTED_FAILURE)
              .toList();
      if (harnessState.entries().isEmpty()
          && harnessOrphans.isEmpty()
          && harnessState.integrityProblems().isEmpty()) {
        continue;
      }
      allEntries.addAll(harnessState.entries());
      scopedSeedInputPaths.addAll(
          harnessState.entries().stream().map(RegressionSeedCatalogEntry::inputPath).toList());
      orphanedInputPaths.addAll(harnessOrphans);
      unexpectedFailureSeeds.addAll(harnessUnexpectedFailureSeeds);
      integrityProblems.addAll(harnessState.integrityProblems());
      targets.add(
          new RegressionSeedTargetAudit(
              harness.key(),
              harnessState.entries().size(),
              harnessState.entries(),
              harnessOrphans,
              harnessUnexpectedFailureSeeds,
              harnessState.integrityProblems()));
    }
    List<RegressionSeedDuplicateContent> duplicateContentGroups =
        RegressionSeedDigests.duplicateContentGroupsForHarnesses(
                projectDirectory, List.of(JazzerHarness.values()))
            .stream()
            .filter(
                duplicateGroup ->
                    duplicateGroup.inputPaths().stream().anyMatch(scopedSeedInputPaths::contains))
            .toList();
    return new RegressionSeedAuditReport(
        allEntries.size(),
        allEntries.stream()
            .map(RegressionSeedCatalogEntry::sha256)
            .collect(java.util.stream.Collectors.toSet())
            .size(),
        orphanedInputPaths.size(),
        unexpectedFailureSeeds.size(),
        integrityProblems.size(),
        targets,
        duplicateContentGroups,
        orphanedInputPaths,
        unexpectedFailureSeeds,
        integrityProblems);
  }

  private static HarnessAuditState newHarnessAuditState() {
    return new HarnessAuditState(new ArrayList<>(), new ArrayList<>(), new HashSet<>());
  }

  private record HarnessAuditState(
      List<RegressionSeedCatalogEntry> entries,
      List<RegressionSeedIntegrityProblem> integrityProblems,
      Set<Path> referencedInputs) {}
}
