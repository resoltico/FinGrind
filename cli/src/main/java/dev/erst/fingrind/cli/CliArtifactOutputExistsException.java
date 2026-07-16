package dev.erst.fingrind.cli;

import java.nio.file.Path;
import java.util.Objects;

/** Deterministic refusal raised when one requested artifact output already exists. */
final class CliArtifactOutputExistsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final Path outputPath;
  private final String artifactOptionName;

  CliArtifactOutputExistsException(Path outputPath, String artifactOptionName) {
    super("The requested artifact destination already exists and will not be overwritten.");
    this.artifactOptionName = Objects.requireNonNull(artifactOptionName, "artifactOptionName");
    this.outputPath = Objects.requireNonNull(outputPath, "outputPath");
  }

  Path outputPath() {
    return outputPath;
  }

  String artifactOptionName() {
    return artifactOptionName;
  }
}
