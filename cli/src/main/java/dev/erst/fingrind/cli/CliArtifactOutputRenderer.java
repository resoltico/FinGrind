package dev.erst.fingrind.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Renders successful non-JSON artifact publication confirmations. */
final class CliArtifactOutputRenderer {
  private CliArtifactOutputRenderer() {}

  static String renderPdfArtifact(Path outputPath) {
    Objects.requireNonNull(outputPath, "outputPath");
    return CliTextFormat.renderTitledBlock(
        "Artifact",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Format", "pdf"),
                List.of("Path", CliPublicPaths.redactedValue(outputPath)))));
  }
}
