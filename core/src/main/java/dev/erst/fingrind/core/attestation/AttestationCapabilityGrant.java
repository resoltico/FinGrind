package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** One append-only principal capability decision. */
record AttestationCapabilityGrant(
    BigInteger acceptedOrder,
    UUID principalId,
    AttestationCapability capability,
    AttestationGrantState state) {
  AttestationCapabilityGrant {
    acceptedOrder =
        AttestationUnsignedEncoding.requireUnsigned(acceptedOrder, Long.BYTES, "acceptedOrder");
    Objects.requireNonNull(principalId, "principalId");
    Objects.requireNonNull(capability, "capability");
    Objects.requireNonNull(state, "state");
  }
}
