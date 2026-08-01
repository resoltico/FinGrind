package dev.erst.fingrind.core.attestation;

/** Canonical lifecycle verb stored in the first field of every effect record. */
public enum AttestationEffectMutation {
  CREATE(0),
  AMEND(1),
  RETIRE(2),
  REACTIVATE(3),
  REVERSE(4),
  DERIVE(5),
  ACKNOWLEDGE(6);

  private final int wireValue;

  AttestationEffectMutation(int wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the fixed unsigned-byte encoding defined by the public attestation catalog. */
  public int wireValue() {
    return wireValue;
  }
}
