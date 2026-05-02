package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Reads and replay-classifies raw libFuzzer finding artifacts from FinGrind's local Jazzer runs.
 */
public final class JazzerFindingSupport {
  private static final List<String> RAW_FINDING_PREFIXES =
      List.of("crash-", "timeout-", "oom-", "leak-", "slow-unit-");

  private JazzerFindingSupport() {}

  /** Returns all replay-classified raw finding artifacts for one replayable Jazzer target. */
  public static List<FindingArtifact> findingArtifacts(
      Path projectDirectory, JazzerRunTarget target) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(target, "target must not be null");
    if (!target.replayable()) {
      throw new IllegalArgumentException(
          "Finding inspection requires a single-harness target, not " + target.key());
    }

    Path runDirectory = target.workingDirectory(projectDirectory);
    if (!Files.isDirectory(runDirectory)) {
      return List.of();
    }

    try (Stream<Path> pathStream = Files.list(runDirectory)) {
      return pathStream
          .filter(Files::isRegularFile)
          .filter(JazzerFindingSupport::isRawFindingArtifact)
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .map(path -> findingArtifact(target, path))
          .toList();
    }
  }

  static FindingArtifact findingArtifact(JazzerRunTarget target, Path rawArtifactPath) {
    try {
      ReplayOutcome outcome =
          JazzerReplayRunner.replay(target.replayHarness(), Files.readAllBytes(rawArtifactPath));
      return new FindingArtifact(
          target.key(),
          rawArtifactKind(rawArtifactPath),
          rawArtifactPath.getFileName().toString(),
          rawArtifactPath.toAbsolutePath().normalize().toString(),
          replayClassification(outcome),
          replayMessage(outcome));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Unable to read raw finding artifact: " + rawArtifactPath.toAbsolutePath().normalize(),
          exception);
    }
  }

  /** Returns the stable replay classification used for raw finding listings. */
  public static ReplayFindingClassification replayClassification(ReplayOutcome outcome) {
    return ReplayFindingClassification.fromOutcome(outcome);
  }

  /** Returns the human-readable replay message associated with one raw finding classification. */
  public static String replayMessage(ReplayOutcome outcome) {
    return Objects.requireNonNull(outcome, "outcome must not be null").message();
  }

  private static boolean isRawFindingArtifact(Path path) {
    String fileName = path.getFileName().toString();
    return RAW_FINDING_PREFIXES.stream().anyMatch(fileName::startsWith);
  }

  static String rawArtifactKind(Path rawArtifactPath) {
    String fileName = rawArtifactPath.getFileName().toString();
    int separator = fileName.indexOf('-');
    return separator >= 0 ? fileName.substring(0, separator) : fileName;
  }
}
