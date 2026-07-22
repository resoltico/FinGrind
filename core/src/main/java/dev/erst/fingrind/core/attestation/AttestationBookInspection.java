package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/**
 * One successful complete-chain verification together with its reconstructed authority read model.
 */
public record AttestationBookInspection(
    AttestationVerification verification, AttestationRegistryInspection registry) {
  public AttestationBookInspection {
    Objects.requireNonNull(verification, "verification");
    Objects.requireNonNull(registry, "registry");
    if (!verification.bookId().equals(registry.bookId())
        || !verification.headOrder().equals(registry.headOrder())) {
      throw new IllegalArgumentException(
          "Verification and registry inspection must describe one head.");
    }
  }
}
