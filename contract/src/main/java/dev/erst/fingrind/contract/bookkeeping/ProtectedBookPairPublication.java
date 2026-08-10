package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionResult;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Completed protected-book and generated-secret publication under one authenticated transaction.
 *
 * <p>This public fact names only final artifacts and the successful transaction result. It never
 * exposes a staged pathname, digest, or cleanup capability.
 */
public record ProtectedBookPairPublication(
    PublicationTransactionArtifact bookPublication,
    PublicationTransactionArtifact generatedSecretPublication) {
  /** Requires two distinct final members published by the same completed transaction. */
  public ProtectedBookPairPublication {
    PublicationTransactionArtifact checkedBookPublication =
        Objects.requireNonNull(bookPublication, "bookPublication");
    PublicationTransactionArtifact checkedGeneratedSecretPublication =
        Objects.requireNonNull(generatedSecretPublication, "generatedSecretPublication");
    if (checkedBookPublication
        .publishedArtifactPath()
        .equals(checkedGeneratedSecretPublication.publishedArtifactPath())) {
      throw new IllegalArgumentException(
          "Protected-book pair publication requires distinct final member artifacts.");
    }
    if (!checkedBookPublication
        .transactionResult()
        .transactionId()
        .equals(checkedGeneratedSecretPublication.transactionResult().transactionId())) {
      throw new IllegalArgumentException(
          "Protected-book pair publication members must share one publication transaction.");
    }
  }

  /** Returns the only recovery handle and durable outcome for the completed pair. */
  public PublicationTransactionResult publicationTransaction() {
    return bookPublication.transactionResult();
  }

  /** Requires the published protected-book member to match the supplied final artifact. */
  public PublicationTransactionArtifact requireBookPublication(Path expectedFinalArtifactPath) {
    return requirePublication(bookPublication, expectedFinalArtifactPath, "bookPublication");
  }

  /** Requires the published generated-secret member to match the supplied final artifact. */
  public PublicationTransactionArtifact requireGeneratedSecretPublication(
      Path expectedFinalArtifactPath) {
    return requirePublication(
        generatedSecretPublication, expectedFinalArtifactPath, "generatedSecretPublication");
  }

  private static PublicationTransactionArtifact requirePublication(
      PublicationTransactionArtifact publication, Path expectedFinalArtifactPath, String name) {
    PublicationTransactionArtifact checkedPublication = Objects.requireNonNull(publication, name);
    Path expected =
        Objects.requireNonNull(expectedFinalArtifactPath, "expectedFinalArtifactPath")
            .toAbsolutePath()
            .normalize();
    if (!checkedPublication.publishedArtifactPath().equals(expected)) {
      throw new IllegalArgumentException(
          "The authoritative publication does not match the protected-book pair final artifact.");
    }
    return checkedPublication;
  }
}
