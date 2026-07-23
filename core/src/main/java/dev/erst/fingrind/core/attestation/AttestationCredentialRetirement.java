package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** One irreversible terminal transition for a previously enrolled credential. */
record AttestationCredentialRetirement(
    BigInteger acceptedOrder,
    UUID principalId,
    AttestationHash keyId,
    AttestationCredentialRetirementState state) {
  AttestationCredentialRetirement {
    acceptedOrder =
        AttestationUnsignedEncoding.requireUnsigned(acceptedOrder, Long.BYTES, "acceptedOrder");
    Objects.requireNonNull(principalId, "principalId");
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(state, "state");
  }
}
