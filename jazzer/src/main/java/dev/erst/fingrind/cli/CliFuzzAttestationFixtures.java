package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/** Synthetic attestation read models used only where a fixture needs an opened-book outcome. */
final class CliFuzzAttestationFixtures {
  private static final UUID SYNTHETIC_BOOK_ID =
      UUID.fromString("20314253-6475-8697-a8b9-cadbecfd0e1f");

  private CliFuzzAttestationFixtures() {}

  /** Returns an empty, structurally valid registry inspection for synthetic outcome fixtures. */
  static AttestationRegistryInspection syntheticTrustRoot() {
    return new AttestationRegistryInspection(
        SYNTHETIC_BOOK_ID,
        BigInteger.ZERO,
        "0".repeat(64),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
