package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import java.util.List;
import java.util.Objects;

/** Fully signed genesis evidence and the founder-key artifacts this preparation retained. */
public record AttestationGenesisPreparation(
    AttestationEvidence evidence, List<ArtifactPublicationResult> retainedFounderKeyArtifacts) {
  /** Retains only immutable, canonical publication facts for a completed preparation. */
  public AttestationGenesisPreparation {
    Objects.requireNonNull(evidence, "evidence");
    retainedFounderKeyArtifacts =
        List.copyOf(
            Objects.requireNonNull(retainedFounderKeyArtifacts, "retainedFounderKeyArtifacts"));
  }
}
