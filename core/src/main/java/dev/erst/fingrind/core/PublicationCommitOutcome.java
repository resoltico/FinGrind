package dev.erst.fingrind.core;

/** The independently reported commit outcome of a publication transaction. */
public enum PublicationCommitOutcome {
  NONE_COMMITTED("none-committed"),
  ALL_COMMITTED("all-committed"),
  PARTIALLY_COMMITTED("partially-committed"),
  COMMIT_UNCERTAIN("commit-uncertain");

  private final String wireValue;

  PublicationCommitOutcome(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the stable lowercase wire value of this independent commit outcome. */
  public String wireValue() {
    return wireValue;
  }

  static PublicationCommitOutcome fromWireValue(String wireValue) {
    for (PublicationCommitOutcome value : values()) {
      if (value.wireValue.equals(wireValue)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unsupported publication commit outcome: " + wireValue);
  }
}
