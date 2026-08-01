package dev.erst.fingrind.core.attestation;

/** Closed terminal lifecycle states for an enrolled attestation credential. */
enum AttestationCredentialRetirementState {
  SUPERSEDED("superseded"),
  REVOKED("revoked");

  private final String token;

  AttestationCredentialRetirementState(String token) {
    this.token = token;
  }

  String token() {
    return token;
  }
}
