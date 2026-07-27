package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;

/** Deterministic refusal raised when one requested artifact output already exists. */
final class CliArtifactOutputExistsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient Path outputPath;
  private final String serializedOutputPath;
  private final String artifactOptionName;
  private final transient ArtifactPublicationRetention retainedStage;
  private final String serializedRetainedStagePath;

  CliArtifactOutputExistsException(
      Path outputPath,
      String artifactOptionName,
      ArtifactPublicationRetention retainedStage,
      FileAlreadyExistsException cause) {
    super(
        "The requested artifact destination already exists and will not be overwritten.",
        Objects.requireNonNull(cause, "cause"));
    this.artifactOptionName = Objects.requireNonNull(artifactOptionName, "artifactOptionName");
    this.outputPath = CliExceptionPathSnapshot.capture(outputPath);
    this.serializedOutputPath = this.outputPath.toString();
    this.retainedStage = Objects.requireNonNull(retainedStage, "retainedStage");
    this.serializedRetainedStagePath = this.retainedStage.retainedStagePath().toString();
  }

  Path outputPath() {
    return outputPath == null ? CliExceptionPathSnapshot.restore(serializedOutputPath) : outputPath;
  }

  String artifactOptionName() {
    return artifactOptionName;
  }

  ArtifactPublicationRetention retainedStage() {
    return retainedStage == null
        ? new ArtifactPublicationRetention(Path.of(serializedRetainedStagePath))
        : retainedStage;
  }
}
