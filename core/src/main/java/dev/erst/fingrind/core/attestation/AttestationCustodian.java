package dev.erst.fingrind.core.attestation;

/** Supported private-key custody choices for version-one attestation. */
enum AttestationCustodian {
  FILE_PKCS8;

  static AttestationCustodian require(String value) {
    if ("file-pkcs8".equals(value)) {
      return FILE_PKCS8;
    }
    throw new AttestationCustodianNotSupportedException(value);
  }
}
