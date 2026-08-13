package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.util.List;
import java.util.Objects;

/** Renders successful non-JSON artifact publication confirmations. */
final class CliArtifactOutputRenderer {
  private CliArtifactOutputRenderer() {}

  static String renderPdfArtifact(PublicationTransactionArtifact publication) {
    PublicationTransactionArtifact published = Objects.requireNonNull(publication, "publication");
    return CliTextFormat.renderTitledBlock(
        "Artifact",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Format", "pdf"),
                List.of("Path", CliPublicPaths.redactedValue(published.publishedArtifactPath())),
                List.of(
                    "Publication transaction",
                    published.transactionResult().transactionId().value()))));
  }
}
