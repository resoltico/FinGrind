package dev.erst.fingrind.core.attestation;

/**
 * A valid receipt condition that affects the anchor's operational value, not its cryptographic
 * validity.
 */
enum AttestationReceiptFinding {
  NOT_INDEPENDENT("receipt-not-independent");

  private final String code;

  AttestationReceiptFinding(String code) {
    this.code = code;
  }

  String code() {
    return code;
  }
}
