package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable final-and-stage evidence for one completed protected-book pair publication.
 *
 * <p>Each member binds its authoritative final target to the exact private stage from which it was
 * published. These are evidence, not cleanup handles: callers must not delete, replace, or reuse
 * either stage, and protected-book recovery never accepts either path as user-supplied input.
 */
public record ProtectedBookPairPublicationRetention(
    ArtifactPublicationResult bookPublication,
    ArtifactPublicationResult generatedSecretPublication) {
  /** Validates two distinct final-and-retained member facts. */
  public ProtectedBookPairPublicationRetention {
    Objects.requireNonNull(bookPublication, "bookPublication");
    Objects.requireNonNull(generatedSecretPublication, "generatedSecretPublication");
    if (bookPublication
        .publishedArtifactPath()
        .equals(generatedSecretPublication.publishedArtifactPath())) {
      throw new IllegalArgumentException(
          "Protected-book pair publication retention requires distinct final member artifacts.");
    }
    if (bookPublication
        .retention()
        .retainedStagePath()
        .equals(generatedSecretPublication.retention().retainedStagePath())) {
      throw new IllegalArgumentException(
          "Protected-book pair publication retention requires distinct member stages.");
    }
    if (bookPublication
            .publishedArtifactPath()
            .equals(generatedSecretPublication.retention().retainedStagePath())
        || generatedSecretPublication
            .publishedArtifactPath()
            .equals(bookPublication.retention().retainedStagePath())) {
      throw new IllegalArgumentException(
          "Protected-book pair publication retention requires four distinct final and stage paths.");
    }
  }

  /** Requires the authoritative primary-book publication to name the supplied final artifact. */
  public ArtifactPublicationResult requireBookPublication(Path expectedFinalArtifactPath) {
    return requirePublication(
        bookPublication, expectedFinalArtifactPath, "bookPublication", "book final artifact");
  }

  /**
   * Requires the authoritative generated-secret publication to name the supplied final artifact.
   */
  public ArtifactPublicationResult requireGeneratedSecretPublication(
      Path expectedFinalArtifactPath) {
    return requirePublication(
        generatedSecretPublication,
        expectedFinalArtifactPath,
        "generatedSecretPublication",
        "generated-secret final artifact");
  }

  private static ArtifactPublicationResult requirePublication(
      ArtifactPublicationResult publication,
      Path expectedFinalArtifactPath,
      String publicationName,
      String artifactName) {
    ArtifactPublicationResult checkedPublication =
        Objects.requireNonNull(publication, publicationName);
    Path expected = canonicalPublicationPath(expectedFinalArtifactPath, artifactName);
    if (!checkedPublication.publishedArtifactPath().equals(expected)) {
      throw new IllegalArgumentException(
          "The authoritative "
              + artifactName
              + " does not match the published protected-book pair fact.");
    }
    return checkedPublication;
  }

  private static Path canonicalPublicationPath(Path path, String parameterName) {
    Path normalized = Objects.requireNonNull(path, parameterName).toAbsolutePath().normalize();
    Path fileName = normalized.getFileName();
    if (fileName == null) {
      throw new IllegalArgumentException(
          parameterName + " must name an artifact in a parent directory.");
    }
    Path parent = Objects.requireNonNull(normalized.getParent(), "publication parent");
    try {
      return parent.toRealPath().resolve(fileName);
    } catch (NoSuchFileException exception) {
      return normalized;
    } catch (IOException | SecurityException exception) {
      throw new IllegalArgumentException(
          parameterName + " must have a resolvable parent directory.", exception);
    }
  }
}
