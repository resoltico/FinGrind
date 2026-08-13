package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import java.util.List;
import java.util.Objects;

/** Fully signed genesis evidence and every founder key published by a completed transaction. */
public record AttestationGenesisPreparation(
    AttestationEvidence evidence,
    List<PublicationTransactionArtifact> publishedFounderKeyArtifacts) {
  /** Retains only completed, canonical transaction artifacts for a completed preparation. */
  public AttestationGenesisPreparation {
    Objects.requireNonNull(evidence, "evidence");
    publishedFounderKeyArtifacts =
        List.copyOf(
            Objects.requireNonNull(publishedFounderKeyArtifacts, "publishedFounderKeyArtifacts"));
  }
}
