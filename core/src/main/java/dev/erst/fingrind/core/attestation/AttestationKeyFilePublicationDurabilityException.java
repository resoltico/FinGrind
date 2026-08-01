package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Reports that a new encrypted key file exists but its parent-directory durability is unconfirmed.
 */
public final class AttestationKeyFilePublicationDurabilityException extends IOException {
  private static final long serialVersionUID = 1L;

  private final transient ArtifactPublicationResult publication;
  private final SerializedPublication serializedPublication;

  /** Retains the published key fact and its immutable retained-stage evidence. */
  public AttestationKeyFilePublicationDurabilityException(
      ArtifactPublicationResult publication, Throwable cause) {
    super(
        "The new attestation key file was published, but FinGrind could not confirm its directory"
            + " durability.",
        Objects.requireNonNull(cause, "cause"));
    this.publication = Objects.requireNonNull(publication, "publication");
    this.serializedPublication = new SerializedPublication(this.publication);
  }

  /** Returns the completed no-clobber publication and its immutable retained-stage evidence. */
  public ArtifactPublicationResult publication() {
    return publication == null ? serializedPublication.restore() : publication;
  }

  /** Serializable replacement for the live publication's opaque filesystem path implementations. */
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
