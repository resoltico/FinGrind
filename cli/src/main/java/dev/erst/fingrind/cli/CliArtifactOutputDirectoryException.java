package dev.erst.fingrind.cli;

import java.nio.file.Path;
import java.util.Objects;

/** Deterministic refusal raised when an artifact cannot be staged in a private output directory. */
final class CliArtifactOutputDirectoryException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String artifactLabel;
  private final String artifactOptionName;
  private final transient Path outputPath;
  private final String serializedOutputPath;

  CliArtifactOutputDirectoryException(
      Path outputPath, String artifactOptionName, String artifactLabel) {
    super(
        "The artifact output parent must be an existing real private directory whose resolved"
            + " ancestry resists non-owner substitution.");
    this.artifactLabel = Objects.requireNonNull(artifactLabel, "artifactLabel");
    this.artifactOptionName = Objects.requireNonNull(artifactOptionName, "artifactOptionName");
    this.outputPath = CliExceptionPathSnapshot.capture(outputPath);
    this.serializedOutputPath = this.outputPath.toString();
  }

  CliArtifactOutputDirectoryException(
      Path outputPath, String artifactOptionName, String artifactLabel, Throwable cause) {
    super(
        "The artifact output parent must be an existing real private directory whose resolved"
            + " ancestry resists non-owner substitution.",
        Objects.requireNonNull(cause, "cause"));
    this.artifactLabel = Objects.requireNonNull(artifactLabel, "artifactLabel");
    this.artifactOptionName = Objects.requireNonNull(artifactOptionName, "artifactOptionName");
    this.outputPath = CliExceptionPathSnapshot.capture(outputPath);
    this.serializedOutputPath = this.outputPath.toString();
  }

  String artifactLabel() {
    return artifactLabel;
  }

  String artifactOptionName() {
    return artifactOptionName;
  }

  Path outputPath() {
    return outputPath == null ? CliExceptionPathSnapshot.restore(serializedOutputPath) : outputPath;
  }
}
