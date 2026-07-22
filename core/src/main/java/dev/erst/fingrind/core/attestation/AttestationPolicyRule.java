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
    if (quorum < AttestationAuthorizationLimits.MINIMUM_QUORUM
        || quorum > AttestationAuthorizationLimits.MAXIMUM_QUORUM) {
      throw new IllegalArgumentException(
          "Attestation policy quorum must be between "
              + AttestationAuthorizationLimits.MINIMUM_QUORUM
              + " and "
              + AttestationAuthorizationLimits.MAXIMUM_QUORUM
              + ".");
    }
  }
}
