package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Closed custody mechanisms that can supply attestation signing credentials. */
public enum AttestationCustodian {
  FILE_PKCS8("file-pkcs8");

  private final String wireValue;

  AttestationCustodian(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the stable public token used to select this custodian. */
  public String wireValue() {
    return wireValue;
  }

  /** Resolves one exact supported custody token without aliases or normalization. */
  public static AttestationCustodian require(String wireValue) {
    String checkedWireValue = Objects.requireNonNull(wireValue, "wireValue");
    for (AttestationCustodian custodian : values()) {
      if (custodian.wireValue.equals(checkedWireValue)) {
        return custodian;
      }
    }
    throw new AttestationCustodianNotSupportedException(checkedWireValue);
  }
}
