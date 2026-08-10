package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Records the public credential and canonical physical path produced by one no-clobber creation.
 */
public record AttestationKeyFileCreation(
    PublicationTransactionArtifact publication, AttestationPublicCredential credential) {
  public AttestationKeyFileCreation {
    Objects.requireNonNull(publication, "publication");
    Objects.requireNonNull(credential, "credential");
  }

  /** Returns the canonical physical path of the published encrypted key file. */
  public Path keyFilePath() {
    return publication.publishedArtifactPath();
  }
}
