package dev.erst.fingrind.core;

/** The independently reported private-stage cleanup outcome of a publication transaction. */
public enum PublicationCleanupOutcome {
  COMPLETE("complete"),
  INCOMPLETE("incomplete"),
  UNCERTAIN("uncertain");

  private final String wireValue;

  PublicationCleanupOutcome(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  static PublicationCleanupOutcome fromWireValue(String wireValue) {
    for (PublicationCleanupOutcome value : values()) {
      if (value.wireValue.equals(wireValue)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unsupported publication cleanup outcome: " + wireValue);
  }
}
