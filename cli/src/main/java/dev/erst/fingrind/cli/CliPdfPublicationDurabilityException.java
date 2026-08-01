package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;

/** Reports a PDF publication whose final-link durability could not be confirmed. */
final class CliPdfPublicationDurabilityException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient ArtifactPublicationResult publication;
  private final SerializedPublication serializedPublication;

  CliPdfPublicationDurabilityException(ArtifactPublicationResult publication, Throwable cause) {
    super(
        "The PDF artifact was published, but FinGrind could not confirm its directory durability.",
        Objects.requireNonNull(cause, "cause"));
    this.publication = Objects.requireNonNull(publication, "publication");
    this.serializedPublication = new SerializedPublication(this.publication);
  }

  ArtifactPublicationResult publication() {
    return publication == null ? serializedPublication.restore() : publication;
  }

  /**
   * Serializable replacement for the live publication's potentially opaque path implementations.
   */
  record SerializedPublication(String publishedArtifactPath, String retainedStagePath)
      implements Serializable {
    private static final long serialVersionUID = 1L;

    SerializedPublication {
      Objects.requireNonNull(publishedArtifactPath, "publishedArtifactPath");
      Objects.requireNonNull(retainedStagePath, "retainedStagePath");
    }

    SerializedPublication(ArtifactPublicationResult publication) {
      this(
          Objects.requireNonNull(publication, "publication").publishedArtifactPath().toString(),
          publication.retention().retainedStagePath().toString());
    }

    ArtifactPublicationResult restore() {
      return ArtifactPublicationResult.restoreCapturedCanonicalPaths(
          Path.of(publishedArtifactPath),
          new ArtifactPublicationRetention(Path.of(retainedStagePath)));
    }
  }
}
