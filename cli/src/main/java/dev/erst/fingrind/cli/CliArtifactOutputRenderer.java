package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.util.List;
import java.util.Objects;

/** Renders successful non-JSON artifact publication confirmations. */
final class CliArtifactOutputRenderer {
  private CliArtifactOutputRenderer() {}

  static String renderPdfArtifact(ArtifactPublicationResult publication) {
    ArtifactPublicationResult published = Objects.requireNonNull(publication, "publication");
    return CliTextFormat.renderTitledBlock(
        "Artifact",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Format", "pdf"),
                List.of("Path", CliPublicPaths.redactedValue(published.publishedArtifactPath())),
                List.of(
                    "Retained stage",
                    CliPublicPaths.redactedValue(published.retention().retainedStagePath())))));
  }
}
