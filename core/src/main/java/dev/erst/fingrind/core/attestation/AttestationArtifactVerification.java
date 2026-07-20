package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/**
 * Successful backup-artifact verification anchored in the chain reconstructed from its snapshot.
 */
record AttestationArtifactVerification(
    AttestationDecodedArtifact artifact, AttestationBookVerification snapshotVerification) {
  AttestationArtifactVerification {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(snapshotVerification, "snapshotVerification");
  }
}
