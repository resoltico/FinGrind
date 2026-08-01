package dev.erst.fingrind.core.attestation;

/** Latest append-only capability grant state for a principal. */
public enum AttestationGrantState {
  GRANT("grant"),
  REVOKE("revoke");

  private final String token;

  AttestationGrantState(String token) {
    this.token = token;
  }

  /** Returns the exact lowercase capability-grant token encoded into attestation preimages. */
  public String token() {
    return token;
  }
}
