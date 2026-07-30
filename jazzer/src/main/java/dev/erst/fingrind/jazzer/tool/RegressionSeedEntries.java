package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Materializes valid committed regression-seed entries from metadata files. */
final class RegressionSeedEntries {
  private RegressionSeedEntries() {}

  static List<RegressionSeedCatalogEntry> entries(Path projectDirectory, JazzerHarness harness)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    Path canonicalProjectDirectory =
        RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(projectDirectory);
    List<RegressionSeedCatalogEntry> entries = new ArrayList<>();
    for (Path metadataPath :
        RegressionSeedPaths.metadataPaths(canonicalProjectDirectory, harness)) {
      RegressionSeedMetadataInspection inspection =
          RegressionSeedMetadataInspector.inspectMetadataPath(
              canonicalProjectDirectory, harness, metadataPath);
      if (inspection.problem() != null) {
        throw new IllegalStateException(inspection.problem().message());
      }
      entries.add(inspection.entry());
    }
    entries.sort(Comparator.comparing(RegressionSeedCatalogEntry::inputPath));
    return entries;
  }

  static List<RegressionSeedCatalogEntry> entries(Path projectDirectory) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Path canonicalProjectDirectory =
        RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(projectDirectory);
    List<RegressionSeedCatalogEntry> entries = new ArrayList<>();
    for (JazzerHarness harness : JazzerHarness.values()) {
      entries.addAll(entries(canonicalProjectDirectory, harness));
    }
    entries.sort(
        Comparator.comparing(RegressionSeedCatalogEntry::targetKey)
            .thenComparing(RegressionSeedCatalogEntry::inputPath));
    return entries;
  }
}
