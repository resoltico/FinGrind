package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Rejects an explicit custody selection that FinGrind does not implement. */
public final class AttestationCustodianNotSupportedException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;
  private final String custodian;

  AttestationCustodianNotSupportedException(String custodian) {
    super("custodian-not-supported: " + Objects.requireNonNull(custodian, "custodian"));
    this.custodian = custodian;
  }

  /** Returns the exact unsupported custody token supplied at the selection boundary. */
  public String custodian() {
    return custodian;
  }
}
