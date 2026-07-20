package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** One irreversible credential retirement fact. */
record AttestationCredentialRevocation(
    BigInteger acceptedOrder, UUID principalId, AttestationHash keyId) {
  AttestationCredentialRevocation {
    acceptedOrder =
        AttestationUnsignedEncoding.requireUnsigned(acceptedOrder, Long.BYTES, "acceptedOrder");
    Objects.requireNonNull(principalId, "principalId");
    Objects.requireNonNull(keyId, "keyId");
  }
}
