package dev.erst.fingrind.core.attestation;

/**
 * States whether the receipt's retained location is independent of the verified book's trust
 * boundary.
 */
public enum AttestationReceiptRetention {
  INDEPENDENT,
  WITHIN_BOOK_TRUST_BOUNDARY
}
