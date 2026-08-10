package dev.erst.fingrind.core;

/** Records the member-local fact durably known by the publication transaction. */
enum PublicationTransactionMemberProgress {
  PLANNED("planned"),
  STAGED("staged"),
  COMMITTED("committed");

  private final String wireValue;

  PublicationTransactionMemberProgress(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  static PublicationTransactionMemberProgress fromWireValue(String wireValue) {
    for (PublicationTransactionMemberProgress value : values()) {
      if (value.wireValue.equals(wireValue)) {
        return value;
      }
    }
    throw new IllegalArgumentException(
        "Unsupported publication transaction member progress: " + wireValue);
  }
}
