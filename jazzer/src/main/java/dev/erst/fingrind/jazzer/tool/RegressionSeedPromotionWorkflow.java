package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Coordinates validation, replay, and durable persistence for one regression-seed promotion. */
final class RegressionSeedPromotionWorkflow {
  private RegressionSeedPromotionWorkflow() {}

  static RegressionSeedPromotionResult promote(
      Path projectDirectory,
      JazzerHarness harness,
      Path sourceInputPath,
      String seedName,
      String coverageIntent,
      RegressionSeedPromoter.ReplayExecutor replayExecutor,
      RegressionSeedPromoter.MetadataWriter metadataWriter)
      throws IOException {
    PromotionRequest request =
        normalizeRequest(
            projectDirectory,
            harness,
            sourceInputPath,
            seedName,
            coverageIntent,
            replayExecutor,
            metadataWriter);
    byte[] inputBytes = Files.readAllBytes(request.sourceInputPath());
    assertNoDuplicateCommittedSeed(
        request.projectDirectory(), inputBytes, request.coverageIntent());
    PromotionPaths promotionPaths =
        resolvePromotionPaths(
            request.projectDirectory(),
            request.harness(),
            request.seedName(),
            request.sourceInputPath());
    ReplayExpectation expectation =
        replayExpectation(request.harness(), inputBytes, request.replayExecutor());
    RegressionSeedMetadata metadata =
        buildMetadata(
            request.projectDirectory(),
            request.harness(),
            promotionPaths.committedInputPath(),
            request.coverageIntent(),
            expectation);
    RegressionSeedPromotionPersistence.persist(
        request.projectDirectory(),
        request.sourceInputPath(),
        inputBytes,
        promotionPaths.committedInputPath(),
        promotionPaths.metadataPath(),
        metadata,
        request.metadataWriter());
    return new RegressionSeedPromotionResult(
        request.harness().key(),
        request.sourceInputPath(),
        promotionPaths.committedInputPath(),
        promotionPaths.metadataPath(),
        request.coverageIntent(),
        expectation);
  }

  private static PromotionRequest normalizeRequest(
      Path projectDirectory,
      JazzerHarness harness,
      Path sourceInputPath,
      String seedName,
      String coverageIntent,
      RegressionSeedPromoter.ReplayExecutor replayExecutor,
      RegressionSeedPromoter.MetadataWriter metadataWriter)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    Objects.requireNonNull(sourceInputPath, "sourceInputPath must not be null");
    Objects.requireNonNull(replayExecutor, "replayExecutor must not be null");
    Objects.requireNonNull(metadataWriter, "metadataWriter must not be null");
    Path normalizedProjectDirectory =
        RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(projectDirectory);
    Path normalizedSourceInputPath = sourceInputPath.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalizedSourceInputPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException(
          "Seed promotion input path must be an existing regular file: "
              + normalizedSourceInputPath);
    }
    Path canonicalSourceInputPath = normalizedSourceInputPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
    return new PromotionRequest(
        normalizedProjectDirectory,
        harness,
        canonicalSourceInputPath,
        RegressionSeedPromoter.normalizeSeedName(seedName),
        ReplayModelValidation.requireText(coverageIntent, "coverageIntent"),
        replayExecutor,
        metadataWriter);
  }

  private static void assertNoDuplicateCommittedSeed(
      Path projectDirectory, byte[] inputBytes, String coverageIntent) throws IOException {
    List<Path> duplicateInputPaths =
        duplicateInputPaths(projectDirectory, RegressionSeedDigests.sha256Hex(inputBytes));
    if (!duplicateInputPaths.isEmpty()) {
      throw new IllegalArgumentException(
          "Committed seed content already exists at: " + joinPaths(duplicateInputPaths));
    }
    List<Path> duplicateCoverageIntentPaths =
        duplicateCoverageIntentMetadataPaths(projectDirectory, coverageIntent);
    if (!duplicateCoverageIntentPaths.isEmpty()) {
      throw new IllegalArgumentException(
          "Committed seed coverage intent already exists at: "
              + joinPaths(duplicateCoverageIntentPaths));
    }
  }

  private static List<Path> duplicateInputPaths(Path projectDirectory, String inputSha256)
      throws IOException {
    List<Path> duplicatePaths = new java.util.ArrayList<>();
    for (Path committedInputPath : RegressionSeedPaths.allInputPaths(projectDirectory)) {
      if (RegressionSeedDigests.sha256Hex(Files.readAllBytes(committedInputPath))
          .equals(inputSha256)) {
        duplicatePaths.add(committedInputPath);
      }
    }
    return List.copyOf(duplicatePaths);
  }

  private static List<Path> duplicateCoverageIntentMetadataPaths(
      Path projectDirectory, String coverageIntent) throws IOException {
    List<Path> duplicatePaths = new java.util.ArrayList<>();
    for (RegressionSeedCatalogEntry entry : RegressionSeedEntries.entries(projectDirectory)) {
      if (entry.coverageIntent().equals(coverageIntent)) {
        duplicatePaths.add(entry.metadataPath());
      }
    }
    return List.copyOf(duplicatePaths);
  }

  private static PromotionPaths resolvePromotionPaths(
      Path projectDirectory, JazzerHarness harness, String seedName, Path sourceInputPath) {
    String inputExtension = preservedExtension(sourceInputPath.getFileName().toString());
    Path committedInputPath =
        harness.inputDirectory(projectDirectory).resolve(seedName + inputExtension);
    Path metadataPath =
        RegressionSeedPaths.metadataDirectory(projectDirectory, harness)
            .resolve(seedName + ".json");
    if (Files.exists(committedInputPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException(
          "Committed seed input path already exists: "
              + committedInputPath.toAbsolutePath().normalize());
    }
    if (Files.exists(metadataPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException(
          "Committed seed metadata path already exists: "
              + metadataPath.toAbsolutePath().normalize());
    }
    return new PromotionPaths(committedInputPath, metadataPath);
  }

  private static String preservedExtension(String fileName) {
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
      return ".bin";
    }
    return fileName.substring(dotIndex);
  }

  private static ReplayExpectation replayExpectation(
      JazzerHarness harness,
      byte[] inputBytes,
      RegressionSeedPromoter.ReplayExecutor replayExecutor) {
    ReplayExpectation expectation =
        JazzerReplayRunner.expectationFor(replayExecutor.replay(harness, inputBytes));
    if (expectation.outcomeKind() == ReplayOutcomeKind.UNEXPECTED_FAILURE) {
      throw new IllegalArgumentException(
          "Seed promotion refuses unexpected-failure replay outcomes; keep this input as a local finding until the bug is fixed: "
              + expectation.message());
    }
    return expectation;
  }

  private static RegressionSeedMetadata buildMetadata(
      Path projectDirectory,
      JazzerHarness harness,
      Path committedInputPath,
      String coverageIntent,
      ReplayExpectation expectation) {
    return new RegressionSeedMetadata(
        harness.key(),
        projectDirectory.relativize(committedInputPath.toAbsolutePath().normalize()).toString(),
        coverageIntent,
        expectation);
  }

  private static String joinPaths(List<Path> paths) {
    return paths.stream()
        .map(Path::toString)
        .sorted()
        .reduce((left, right) -> left + ", " + right)
        .orElse("");
  }

  private record PromotionRequest(
      Path projectDirectory,
      JazzerHarness harness,
      Path sourceInputPath,
      String seedName,
      String coverageIntent,
      RegressionSeedPromoter.ReplayExecutor replayExecutor,
      RegressionSeedPromoter.MetadataWriter metadataWriter) {}

  private record PromotionPaths(Path committedInputPath, Path metadataPath) {}
}
