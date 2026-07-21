package dev.erst.fingrind.core.attestation;

/**
 * A valid receipt condition that affects the anchor's operational value, not its cryptographic
 * validity.
 */
public enum AttestationReceiptFinding {
  NOT_INDEPENDENT("receipt-not-independent");

  private final String code;

  AttestationReceiptFinding(String code) {
    this.code = code;
  }

  /** Returns the stable machine-readable finding code. */
  public String code() {
    return code;
  }
}
