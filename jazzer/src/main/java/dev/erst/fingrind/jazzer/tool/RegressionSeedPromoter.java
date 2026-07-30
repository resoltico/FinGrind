package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Path;

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
    return RegressionSeedPromotionWorkflow.promote(
        projectDirectory,
        harness,
        sourceInputPath,
        seedName,
        coverageIntent,
        replayExecutor,
        metadataWriter);
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
