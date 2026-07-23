package dev.erst.fingrind.core.attestation;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Complete-chain verification together with authenticated posting-to-operation commitments. */
public record AttestationPostingCommitmentInspection(
    AttestationVerification verification,
    Map<UUID, AttestationOperationCommitment> commitmentsByPostingId) {
  /** Defensively owns the verified projection. */
  public AttestationPostingCommitmentInspection {
    Objects.requireNonNull(verification, "verification");
    commitmentsByPostingId =
        Map.copyOf(Objects.requireNonNull(commitmentsByPostingId, "commitmentsByPostingId"));
  }
}
