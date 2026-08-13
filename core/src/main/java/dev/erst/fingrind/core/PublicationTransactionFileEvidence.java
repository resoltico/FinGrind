package dev.erst.fingrind.core;

import java.util.Objects;

/** Fresh no-follow identity and digest facts read from one private transaction artifact. */
record PublicationTransactionFileEvidence(String physicalIdentity, String sha256Hex) {
  PublicationTransactionFileEvidence {
    physicalIdentity =
        PublicationTransactionStagedArtifact.requireNonBlank(physicalIdentity, "physicalIdentity");
    Objects.requireNonNull(sha256Hex, "sha256Hex");
    new PublicationTransactionFinalizedArtifact(physicalIdentity, sha256Hex);
  }
}
