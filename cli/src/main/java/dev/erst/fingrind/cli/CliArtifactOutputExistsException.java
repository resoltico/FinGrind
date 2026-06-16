package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import java.util.Objects;

/** Deterministic refusal raised when one requested artifact output already exists. */
final class CliArtifactOutputExistsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final Path outputPath;
  private final String artifactOptionName;

  CliArtifactOutputExistsException(Path outputPath, String artifactOptionName) {
    super(
        "The requested artifact destination already exists and will not be overwritten: "
            + PublicPathHint.fromPath(Objects.requireNonNull(outputPath, "outputPath")).value());
    this.artifactOptionName = Objects.requireNonNull(artifactOptionName, "artifactOptionName");
    this.outputPath = outputPath;
  }

  Path outputPath() {
    return outputPath;
  }

  String artifactOptionName() {
    return artifactOptionName;
  }
}
