package dev.erst.fingrind.core;

/** Classifies the secret-bearing artifact governed by one publication-journal member. */
public enum PublicationTransactionMemberRole {
  PROTECTED_BOOK("protected-book"),
  ENCRYPTED_BOOK_KEY("encrypted-book-key"),
  ATTESTATION_KEY("attestation-key"),
  ATTESTATION_RECEIPT("attestation-receipt"),
  PDF_REPORT("pdf-report"),
  PASSPHRASE_FILE("passphrase-file");

  private final String wireValue;

  PublicationTransactionMemberRole(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  static PublicationTransactionMemberRole fromWireValue(String wireValue) {
    for (PublicationTransactionMemberRole value : values()) {
      if (value.wireValue.equals(wireValue)) {
        return value;
      }
    }
    throw new IllegalArgumentException(
        "Unsupported publication transaction member role: " + wireValue);
  }
}
