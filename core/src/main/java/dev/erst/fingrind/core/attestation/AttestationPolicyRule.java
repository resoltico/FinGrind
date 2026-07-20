package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;

/** One append-only capability quorum rule. */
record AttestationPolicyRule(
    BigInteger acceptedOrder, AttestationCapability capability, int quorum) {
  AttestationPolicyRule {
    acceptedOrder =
        AttestationUnsignedEncoding.requireUnsigned(acceptedOrder, Long.BYTES, "acceptedOrder");
    Objects.requireNonNull(capability, "capability");
    if (quorum < 1 || quorum > 64) {
      throw new IllegalArgumentException("Attestation policy quorum must be between 1 and 64.");
    }
  }
}
