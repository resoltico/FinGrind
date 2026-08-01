package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Records the public credential and canonical physical path produced by one no-clobber creation.
 */
public record AttestationKeyFileCreation(
    ArtifactPublicationResult publication, AttestationPublicCredential credential) {
  public AttestationKeyFileCreation {
    Objects.requireNonNull(publication, "publication");
    Objects.requireNonNull(credential, "credential");
  }

  /** Returns the canonical physical path of the published encrypted key file. */
  public Path keyFilePath() {
    return publication.publishedArtifactPath();
  }

  /** Returns the retained private stage used for this no-clobber publication. */
  public ArtifactPublicationRetention retainedStage() {
    return publication.retention();
  }
}
