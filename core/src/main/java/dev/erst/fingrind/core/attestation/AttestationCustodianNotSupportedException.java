package dev.erst.fingrind.core.attestation;

/** Rejects a custody mechanism that this attestation format does not implement. */
final class AttestationCustodianNotSupportedException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  AttestationCustodianNotSupportedException(String custodian) {
    super("custodian-not-supported: " + custodian);
  }
}
