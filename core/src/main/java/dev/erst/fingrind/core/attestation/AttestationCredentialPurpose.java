package dev.erst.fingrind.core.attestation;

/** Source-channel purpose permanently attached to a credential binding. */
public enum AttestationCredentialPurpose {
  OPERATOR("operator"),
  SYSTEM("system");

  private final String token;

  AttestationCredentialPurpose(String token) {
    this.token = token;
  }

  /** Returns the exact lowercase credential-purpose token encoded into attestation preimages. */
  public String token() {
    return token;
  }
}
