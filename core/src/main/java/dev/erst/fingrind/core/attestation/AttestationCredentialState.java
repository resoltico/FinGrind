package dev.erst.fingrind.core.attestation;

import org.jspecify.annotations.Nullable;

/** Resolves a key's enrollment state at one historical operation position. */
record AttestationCredentialState(
    @Nullable AttestationCredentialBinding binding, boolean active, boolean revoked) {

  static AttestationCredentialState notEnrolled() {
    return new AttestationCredentialState(null, false, false);
  }

  static AttestationCredentialState active(AttestationCredentialBinding binding) {
    return new AttestationCredentialState(binding, true, false);
  }

  static AttestationCredentialState revoked(AttestationCredentialBinding binding) {
    return new AttestationCredentialState(binding, false, true);
  }

  AttestationCredentialBinding requireBinding() {
    return java.util.Objects.requireNonNull(binding, "credential state has no binding");
  }
}
