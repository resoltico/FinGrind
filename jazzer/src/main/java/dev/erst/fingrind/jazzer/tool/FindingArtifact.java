package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/**
 * Describes one replay-classified raw libFuzzer artifact recorded under {@code jazzer/.local/runs}.
 */
public record FindingArtifact(
    String targetKey,
    String rawArtifactKind,
    String rawArtifactName,
    String rawArtifactPath,
    ReplayFindingClassification replayClassification,
    String message) {
  public FindingArtifact {
    targetKey = ReplayModelValidation.requireText(targetKey, "targetKey");
    rawArtifactKind = ReplayModelValidation.requireText(rawArtifactKind, "rawArtifactKind");
    rawArtifactName = ReplayModelValidation.requireText(rawArtifactName, "rawArtifactName");
    rawArtifactPath = ReplayModelValidation.requireText(rawArtifactPath, "rawArtifactPath");
    Objects.requireNonNull(replayClassification, "replayClassification must not be null");
    message = ReplayModelValidation.requireText(message, "message");
  }
}
