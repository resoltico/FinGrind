package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/** Validates one committed regression-seed metadata document against durable file reality. */
final class RegressionSeedMetadataInspector {
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private RegressionSeedMetadataInspector() {}

  static RegressionSeedMetadataInspection inspectMetadataPath(
      Path projectDirectory, JazzerHarness harness, Path metadataPath) throws IOException {
    Path canonicalProjectDirectory =
        RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(projectDirectory);
    Path normalizedMetadataPath = metadataPath.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalizedMetadataPath, LinkOption.NOFOLLOW_LINKS)) {
      return RegressionSeedMetadataInspection.problem(
          new RegressionSeedIntegrityProblem(
              harness.key(),
              normalizedMetadataPath,
              null,
              "metadata-read-failure",
              "Committed regression metadata must be a regular non-symlink file: "
                  + normalizedMetadataPath));
    }
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
    return inspectReadableMetadata(
        canonicalProjectDirectory, harness, normalizedMetadataPath, metadata);
  }

  private static RegressionSeedMetadataInspection inspectReadableMetadata(
      Path canonicalProjectDirectory,
      JazzerHarness harness,
      Path normalizedMetadataPath,
      RegressionSeedMetadata metadata) {
    Path normalizedInputPath =
        metadata.inputPath(canonicalProjectDirectory).toAbsolutePath().normalize();
    Optional<RegressionSeedMetadataInspection> locationProblem =
        metadataLocationProblem(
            canonicalProjectDirectory,
            harness,
            normalizedMetadataPath,
            normalizedInputPath,
            metadata);
    if (locationProblem.isPresent()) {
      return locationProblem.orElseThrow();
    }
    return inspectReferencedInput(
        canonicalProjectDirectory, harness, normalizedMetadataPath, normalizedInputPath, metadata);
  }

  private static Optional<RegressionSeedMetadataInspection> metadataLocationProblem(
      Path canonicalProjectDirectory,
      JazzerHarness harness,
      Path normalizedMetadataPath,
      Path normalizedInputPath,
      RegressionSeedMetadata metadata) {
    if (!metadata.targetKey().equals(harness.key())) {
      return Optional.of(
          problem(
              harness,
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
        harness.inputDirectory(canonicalProjectDirectory).toAbsolutePath().normalize();
    if (normalizedInputPath.startsWith(normalizedHarnessInputDirectory)) {
      return Optional.empty();
    }
    return Optional.of(
        problem(
            harness,
            normalizedMetadataPath,
            normalizedInputPath,
            "input-outside-harness",
            "Committed regression metadata points outside the owning harness input directory: "
                + normalizedMetadataPath
                + " -> "
                + normalizedInputPath));
  }

  private static RegressionSeedMetadataInspection inspectReferencedInput(
      Path canonicalProjectDirectory,
      JazzerHarness harness,
      Path normalizedMetadataPath,
      Path normalizedInputPath,
      RegressionSeedMetadata metadata) {
    Path inputParent =
        java.util.Objects.requireNonNull(
            normalizedInputPath.getParent(), "absolute normalized input paths have a parent");
    boolean realInputParent;
    try {
      realInputParent =
          RegressionSeedRepositoryPathAdmission.hasExistingRealDirectoryTree(
              canonicalProjectDirectory, inputParent);
    } catch (IOException exception) {
      return problem(
          harness,
          normalizedMetadataPath,
          normalizedInputPath,
          "input-not-regular-file",
          "Committed regression metadata points through a non-real input directory: "
              + normalizedMetadataPath
              + " -> "
              + normalizedInputPath);
    }
    if (!realInputParent || Files.notExists(normalizedInputPath, LinkOption.NOFOLLOW_LINKS)) {
      return problem(
          harness,
          normalizedMetadataPath,
          normalizedInputPath,
          "input-missing",
          "Committed regression metadata points to a missing raw input: "
              + normalizedMetadataPath
              + " -> "
              + normalizedInputPath);
    }
    if (!Files.isRegularFile(normalizedInputPath, LinkOption.NOFOLLOW_LINKS)) {
      return problem(
          harness,
          normalizedMetadataPath,
          normalizedInputPath,
          "input-not-regular-file",
          "Committed regression metadata points to a non-file raw input: "
              + normalizedMetadataPath
              + " -> "
              + normalizedInputPath);
    }
    if (normalizedInputPath.getFileName().toString().endsWith(".json")) {
      try {
        JSON_MAPPER.readTree(Files.readString(normalizedInputPath));
      } catch (IOException exception) {
        return inputReadProblem(harness, normalizedMetadataPath, normalizedInputPath, exception);
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
      return inputReadProblem(harness, normalizedMetadataPath, normalizedInputPath, exception);
    }
  }

  private static RegressionSeedMetadataInspection inputReadProblem(
      JazzerHarness harness, Path metadataPath, Path inputPath, IOException exception) {
    return problem(
        harness,
        metadataPath,
        inputPath,
        "input-read-failure",
        "Committed regression input could not be read: "
            + inputPath
            + " -> "
            + exception.getMessage());
  }

  private static RegressionSeedMetadataInspection problem(
      JazzerHarness harness, Path metadataPath, Path inputPath, String code, String message) {
    return RegressionSeedMetadataInspection.problem(
        new RegressionSeedIntegrityProblem(harness.key(), metadataPath, inputPath, code, message));
  }
}
