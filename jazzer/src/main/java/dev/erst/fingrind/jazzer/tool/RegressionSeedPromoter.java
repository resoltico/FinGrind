package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Promotes one ad hoc replay input into the committed FinGrind regression seed floor. */
public final class RegressionSeedPromoter {
  private RegressionSeedPromoter() {}

  /** Promotes one raw input into the target harness's committed input and metadata directories. */
  public static RegressionSeedPromotionResult promote(
      Path projectDirectory,
      JazzerHarness harness,
      Path sourceInputPath,
      String seedName,
      String coverageIntent)
      throws IOException {
    return promote(
        projectDirectory,
        harness,
        sourceInputPath,
        seedName,
        coverageIntent,
        JazzerReplayRunner::replay,
        JazzerJson::write);
  }

  static RegressionSeedPromotionResult promote(
      Path projectDirectory,
      JazzerHarness harness,
      Path sourceInputPath,
      String seedName,
      String coverageIntent,
      ReplayExecutor replayExecutor)
      throws IOException {
    return promote(
        projectDirectory,
        harness,
        sourceInputPath,
        seedName,
        coverageIntent,
        replayExecutor,
        JazzerJson::write);
  }

  static RegressionSeedPromotionResult promote(
      Path projectDirectory,
      JazzerHarness harness,
      Path sourceInputPath,
      String seedName,
      String coverageIntent,
      ReplayExecutor replayExecutor,
      MetadataWriter metadataWriter)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    Objects.requireNonNull(sourceInputPath, "sourceInputPath must not be null");
    Objects.requireNonNull(replayExecutor, "replayExecutor must not be null");
    Objects.requireNonNull(metadataWriter, "metadataWriter must not be null");
    String normalizedSeedName = normalizeSeedName(seedName);
    String normalizedCoverageIntent =
        ReplayModelValidation.requireText(coverageIntent, "coverageIntent");

    Path normalizedProjectDirectory = projectDirectory.toAbsolutePath().normalize();
    Path normalizedSourceInputPath = sourceInputPath.toAbsolutePath().normalize();
    if (!Files.exists(normalizedSourceInputPath)
        || !Files.isRegularFile(normalizedSourceInputPath)) {
      throw new IllegalArgumentException(
          "Seed promotion input path must be an existing regular file: "
              + normalizedSourceInputPath);
    }

    byte[] inputBytes = Files.readAllBytes(normalizedSourceInputPath);
    String inputSha256 = RegressionSeedCatalog.sha256Hex(inputBytes);
    List<Path> duplicatePaths = duplicateInputPaths(normalizedProjectDirectory, inputSha256);
    if (!duplicatePaths.isEmpty()) {
      throw new IllegalArgumentException(
          "Committed seed content already exists at: " + joinPaths(duplicatePaths));
    }
    List<Path> duplicateCoverageIntentPaths =
        duplicateCoverageIntentMetadataPaths(normalizedProjectDirectory, normalizedCoverageIntent);
    if (!duplicateCoverageIntentPaths.isEmpty()) {
      throw new IllegalArgumentException(
          "Committed seed coverage intent already exists at: "
              + joinPaths(duplicateCoverageIntentPaths));
    }

    String inputExtension = preservedExtension(normalizedSourceInputPath.getFileName().toString());
    Path committedInputPath =
        harness
            .inputDirectory(normalizedProjectDirectory)
            .resolve(normalizedSeedName + inputExtension);
    Path metadataPath =
        RegressionSeedCatalog.metadataDirectory(normalizedProjectDirectory, harness)
            .resolve(normalizedSeedName + ".json");
    if (Files.exists(committedInputPath)) {
      throw new IllegalArgumentException(
          "Committed seed input path already exists: "
              + committedInputPath.toAbsolutePath().normalize());
    }
    if (Files.exists(metadataPath)) {
      throw new IllegalArgumentException(
          "Committed seed metadata path already exists: "
              + metadataPath.toAbsolutePath().normalize());
    }

    ReplayOutcome replayOutcome = replayExecutor.replay(harness, inputBytes);
    ReplayExpectation expectation = JazzerReplayRunner.expectationFor(replayOutcome);
    if (expectation.outcomeKind() == ReplayOutcomeKind.UNEXPECTED_FAILURE) {
      throw new IllegalArgumentException(
          "Seed promotion refuses unexpected-failure replay outcomes; keep this input as a local finding until the bug is fixed: "
              + expectation.message());
    }
    RegressionSeedMetadata metadata =
        new RegressionSeedMetadata(
            harness.key(),
            normalizedProjectDirectory
                .relativize(committedInputPath.toAbsolutePath().normalize())
                .toString(),
            normalizedCoverageIntent,
            expectation);

    Files.createDirectories(committedInputPath.getParent());
    Files.createDirectories(metadataPath.getParent());
    boolean inputCopied = false;
    try {
      Files.copy(normalizedSourceInputPath, committedInputPath);
      inputCopied = true;
      metadataWriter.write(metadataPath, metadata);
    } catch (IOException | RuntimeException exception) {
      if (inputCopied) {
        Files.deleteIfExists(committedInputPath);
      }
      Files.deleteIfExists(metadataPath);
      throw exception;
    }

    return new RegressionSeedPromotionResult(
        harness.key(),
        normalizedSourceInputPath,
        committedInputPath,
        metadataPath,
        normalizedCoverageIntent,
        expectation);
  }

  static String normalizeSeedName(String seedName) {
    String normalized = ReplayModelValidation.requireText(seedName, "seedName");
    if (!normalized.matches("[a-z0-9][a-z0-9_]*")) {
      String suggestion = normalizedSeedNameSuggestion(normalized);
      throw new IllegalArgumentException(
          "seedName must use lower_snake_case ASCII letters, digits, and underscores. Try: "
              + suggestion);
    }
    return normalized;
  }

  static String normalizedSeedNameSuggestion(String seedName) {
    String normalized =
        seedName
            .trim()
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "")
            .replaceAll("_{2,}", "_");
    if (normalized.isEmpty()) {
      return "seed";
    }
    return normalized;
  }

  private static String preservedExtension(String fileName) {
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
      return ".bin";
    }
    return fileName.substring(dotIndex);
  }

  private static String joinPaths(List<Path> paths) {
    return paths.stream()
        .map(Path::toString)
        .sorted()
        .reduce((left, right) -> left + ", " + right)
        .orElse("");
  }

  private static List<Path> duplicateInputPaths(Path projectDirectory, String inputSha256)
      throws IOException {
    List<Path> duplicatePaths = new java.util.ArrayList<>();
    for (Path committedInputPath : RegressionSeedCatalog.allInputPaths(projectDirectory)) {
      if (RegressionSeedCatalog.sha256Hex(Files.readAllBytes(committedInputPath))
          .equals(inputSha256)) {
        duplicatePaths.add(committedInputPath);
      }
    }
    return List.copyOf(duplicatePaths);
  }

  private static List<Path> duplicateCoverageIntentMetadataPaths(
      Path projectDirectory, String coverageIntent) throws IOException {
    List<Path> duplicatePaths = new java.util.ArrayList<>();
    for (RegressionSeedCatalogEntry entry : RegressionSeedCatalog.entries(projectDirectory)) {
      if (entry.coverageIntent().equals(coverageIntent)) {
        duplicatePaths.add(entry.metadataPath());
      }
    }
    return List.copyOf(duplicatePaths);
  }

  /** Executes one replay for the candidate seed bytes being promoted. */
  @FunctionalInterface
  interface ReplayExecutor {
    /** Replays one raw input through the selected harness. */
    ReplayOutcome replay(JazzerHarness harness, byte[] input);
  }

  /** Persists the committed regression metadata that describes one promoted seed. */
  @FunctionalInterface
  interface MetadataWriter {
    /** Writes one metadata document for the promoted seed. */
    void write(Path metadataPath, RegressionSeedMetadata metadata) throws IOException;
  }
}
