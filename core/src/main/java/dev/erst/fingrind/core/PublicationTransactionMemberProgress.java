package dev.erst.fingrind.core;

/** Records the member-local fact durably known by the publication transaction. */
enum PublicationTransactionMemberProgress {
  PLANNED("planned"),
  STAGED("staged"),
  ABORTED("aborted"),
  COMMITTED("committed"),
  CLEANED("cleaned");

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

  /** Returns whether this progress fact preserves or advances the supplied prior fact. */
  boolean canFollow(PublicationTransactionMemberProgress priorProgress) {
    return switch (this) {
      case PLANNED -> priorProgress == PLANNED;
      case STAGED -> priorProgress == PLANNED || priorProgress == STAGED;
      case ABORTED -> priorProgress == STAGED || priorProgress == ABORTED;
      case COMMITTED -> priorProgress == STAGED || priorProgress == COMMITTED;
      case CLEANED -> priorProgress == COMMITTED || priorProgress == CLEANED;
    };
  }
}
