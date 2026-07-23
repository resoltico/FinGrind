package dev.erst.fingrind.core.attestation;

import org.jspecify.annotations.Nullable;

/** Resolves a key's enrollment state at one historical operation position. */
record AttestationCredentialState(
    @Nullable AttestationCredentialBinding binding,
    @Nullable AttestationCredentialRetirementState retirementState) {

  static AttestationCredentialState notEnrolled() {
    return new AttestationCredentialState(null, null);
  }

  static AttestationCredentialState active(AttestationCredentialBinding binding) {
    return new AttestationCredentialState(binding, null);
  }

  static AttestationCredentialState retired(
      AttestationCredentialBinding binding, AttestationCredentialRetirementState state) {
    return new AttestationCredentialState(binding, state);
  }

  boolean active() {
    return binding != null && retirementState == null;
  }

  boolean revoked() {
    return retirementState == AttestationCredentialRetirementState.REVOKED;
  }

  boolean superseded() {
    return retirementState == AttestationCredentialRetirementState.SUPERSEDED;
  }

  AttestationCredentialBinding requireBinding() {
    return java.util.Objects.requireNonNull(binding, "credential state has no binding");
  }
}
