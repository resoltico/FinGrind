package dev.erst.fingrind.core.attestation;

import java.util.Objects;
import java.util.UUID;

/** One genesis founder and its mandatory operator-purpose credential. */
record AttestationFounder(UUID principalId, AttestationHash keyId, AttestationSpki spki) {
  AttestationFounder {
    Objects.requireNonNull(principalId, "principalId");
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(spki, "spki");
    if (!AttestationEd25519.isEd25519Spki(spki.bytes())
        || !keyId.equals(AttestationHash.sha256(spki.bytes()))) {
      throw new IllegalArgumentException(
          "Attestation founder keyId must hash its canonical Ed25519 SPKI.");
    }
    new AttestationCredentialBinding(
        java.math.BigInteger.ZERO,
        principalId,
        keyId,
        AttestationCredentialBinding.BindingAction.ENROLL,
        spki,
        AttestationCredentialPurpose.OPERATOR,
        null);
  }

  AttestationCredentialBinding binding() {
    return new AttestationCredentialBinding(
        java.math.BigInteger.ZERO,
        principalId,
        keyId,
        AttestationCredentialBinding.BindingAction.ENROLL,
        spki,
        AttestationCredentialPurpose.OPERATOR,
        null);
  }
}
