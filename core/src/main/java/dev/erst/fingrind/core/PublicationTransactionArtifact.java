package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Reports one caller-selected final artifact after its owning publication transaction completed.
 *
 * <p>The transaction result is deliberately the only recovery evidence. This value never exposes a
 * staged-secret pathname, digest, identity, or cleanup authority.
 */
public record PublicationTransactionArtifact(
    Path publishedArtifactPath, PublicationTransactionResult transactionResult) {
  /** Requires a final artifact name and the fully successful transaction that published it. */
  public PublicationTransactionArtifact {
    publishedArtifactPath = normalizeFinalPath(publishedArtifactPath);
    Objects.requireNonNull(transactionResult, "transactionResult");
    if (!transactionResult.successful()) {
      throw new IllegalArgumentException(
          "A published artifact requires a complete publication transaction result.");
    }
  }

  private static Path normalizeFinalPath(Path path) {
    Path normalizedPath =
        Objects.requireNonNull(path, "publishedArtifactPath").toAbsolutePath().normalize();
    if (normalizedPath.getFileName() == null) {
      throw new IllegalArgumentException(
          "publishedArtifactPath must name an artifact in a parent directory.");
    }
    return normalizedPath;
  }
}
