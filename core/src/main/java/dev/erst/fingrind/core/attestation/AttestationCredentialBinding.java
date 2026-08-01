package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One append-only credential binding whose identity is its canonical Ed25519 SPKI hash. */
record AttestationCredentialBinding(
    BigInteger acceptedOrder,
    UUID principalId,
    AttestationHash keyId,
    BindingAction action,
    AttestationSpki spki,
    AttestationCredentialPurpose purpose,
    @Nullable AttestationHash predecessorKeyId) {
  AttestationCredentialBinding {
    acceptedOrder =
        AttestationUnsignedEncoding.requireUnsigned(acceptedOrder, Long.BYTES, "acceptedOrder");
    Objects.requireNonNull(principalId, "principalId");
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(spki, "spki");
    Objects.requireNonNull(purpose, "purpose");
    if (!keyId.equals(AttestationHash.sha256(spki.bytes()))) {
      throw new IllegalArgumentException(
          "Attestation credential keyId must hash its supplied SPKI.");
    }
    if ((action == BindingAction.ENROLL) != (predecessorKeyId == null)) {
      throw new IllegalArgumentException(
          "Attestation enroll and rollover predecessor rules must be explicit.");
    }
    if (keyId.equals(predecessorKeyId)) {
      throw new IllegalArgumentException(
          "Attestation rollover predecessor must differ from the new key.");
    }
  }

  /** Closed actions by which an active credential can enter the registry. */
  enum BindingAction {
    ENROLL,
    ROLLOVER
  }
}
