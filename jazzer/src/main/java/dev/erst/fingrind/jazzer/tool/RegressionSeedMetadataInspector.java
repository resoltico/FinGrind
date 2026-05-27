package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;

/** Validates one committed regression-seed metadata document against durable file reality. */
final class RegressionSeedMetadataInspector {
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private RegressionSeedMetadataInspector() {}

  static RegressionSeedMetadataInspection inspectMetadataPath(
      Path projectDirectory, JazzerHarness harness, Path metadataPath) throws IOException {
    Path normalizedMetadataPath = metadataPath.toAbsolutePath().normalize();
    RegressionSeedMetadata metadata;
    try {
      metadata = JazzerJson.read(normalizedMetadataPath, RegressionSeedMetadata.class);
    } catch (IOException | RuntimeException exception) {
      return RegressionSeedMetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              null,
              "metadata-read-failure",
              "Committed regression metadata is unreadable: "
                  + normalizedMetadataPath
                  + " -> "
                  + exception.getMessage()));
    }
    Path normalizedInputPath = metadata.inputPath(projectDirectory).toAbsolutePath().normalize();
    if (!metadata.targetKey().equals(harness.key())) {
      return RegressionSeedMetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "target-mismatch",
              "Committed regression metadata target does not match its owning harness directory: "
                  + normalizedMetadataPath
                  + " declares "
                  + metadata.targetKey()
                  + " but lives under "
                  + harness.key()
                  + "."));
    }

    Path normalizedHarnessInputDirectory =
        harness.inputDirectory(projectDirectory).toAbsolutePath().normalize();
    if (!normalizedInputPath.startsWith(normalizedHarnessInputDirectory)) {
      return RegressionSeedMetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "input-outside-harness",
              "Committed regression metadata points outside the owning harness input directory: "
                  + normalizedMetadataPath
                  + " -> "
                  + normalizedInputPath));
    }
    if (!Files.exists(normalizedInputPath)) {
      return RegressionSeedMetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "input-missing",
              "Committed regression metadata points to a missing raw input: "
                  + normalizedMetadataPath
                  + " -> "
                  + normalizedInputPath));
    }
    if (!Files.isRegularFile(normalizedInputPath)) {
      return RegressionSeedMetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "input-not-regular-file",
              "Committed regression metadata points to a non-file raw input: "
                  + normalizedMetadataPath
                  + " -> "
                  + normalizedInputPath));
    }
    if (normalizedInputPath.getFileName().toString().endsWith(".json")) {
      try {
        JSON_MAPPER.readTree(Files.readString(normalizedInputPath));
      } catch (IOException exception) {
        return RegressionSeedMetadataInspection.problem(
            new RegressionSeedIntegrityProblem(
                harness.key(),
                normalizedMetadataPath,
                normalizedInputPath,
                "input-read-failure",
                "Committed regression input could not be read: "
                    + normalizedInputPath
                    + " -> "
                    + exception.getMessage()));
      } catch (RuntimeException exception) {
        return RegressionSeedMetadataInspection.problem(
            new RegressionSeedIntegrityProblem(
                harness.key(),
                normalizedMetadataPath,
                normalizedInputPath,
                "input-json-malformed",
                "Committed JSON regression input is malformed: "
                    + normalizedInputPath
                    + " -> "
                    + exception.getMessage()));
      }
    }

    try {
      return RegressionSeedMetadataInspection.entry(
          new RegressionSeedCatalogEntry(
              metadata.targetKey(),
              normalizedMetadataPath,
              normalizedInputPath,
              metadata.coverageIntent(),
              metadata.expectation(),
              RegressionSeedDigests.sha256Hex(Files.readAllBytes(normalizedInputPath))));
    } catch (IOException exception) {
      return RegressionSeedMetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              normalizedInputPath,
              "input-read-failure",
              "Committed regression input could not be read: "
                  + normalizedInputPath
                  + " -> "
                  + exception.getMessage()));
    }
  }
}
