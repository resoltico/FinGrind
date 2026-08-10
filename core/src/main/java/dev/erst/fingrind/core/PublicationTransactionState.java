package dev.erst.fingrind.core;

/** Durable states of one authenticated publication transaction. */
public enum PublicationTransactionState {
  PREPARED("prepared"),
  STAGED("staged"),
  COMMITTING("committing"),
  COMMITTED("committed"),
  CLEANING("cleaning"),
  COMPLETE("complete"),
  BLOCKED("blocked"),
  COMMIT_UNCERTAIN("commit-uncertain"),
  CLEANUP_INCOMPLETE("cleanup-incomplete"),
  CLEANUP_UNCERTAIN("cleanup-uncertain");

  private final String wireValue;

  PublicationTransactionState(String wireValue) {
    this.wireValue = wireValue;
  }

  /**
   * Returns the stable lowercase wire value recorded in journals and public transaction evidence.
   */
  public String wireValue() {
    return wireValue;
  }

  static PublicationTransactionState fromWireValue(String wireValue) {
    for (PublicationTransactionState value : values()) {
      if (value.wireValue.equals(wireValue)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unsupported publication transaction state: " + wireValue);
  }

  /** Returns whether recovery rather than normal forward publication is required. */
  public boolean requiresRecovery() {
    return switch (this) {
      case BLOCKED, COMMIT_UNCERTAIN, CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN -> true;
      case PREPARED, STAGED, COMMITTING, COMMITTED, CLEANING, COMPLETE -> false;
    };
  }

  /** Returns whether this state prevents every further owner-authorized recovery transition. */
  public boolean terminal() {
    return switch (this) {
      case COMPLETE, BLOCKED -> true;
      case PREPARED,
          STAGED,
          COMMITTING,
          COMMITTED,
          CLEANING,
          COMMIT_UNCERTAIN,
          CLEANUP_INCOMPLETE,
          CLEANUP_UNCERTAIN ->
          false;
    };
  }

  /** Returns whether the next owner-authorized durable state is safe from this state. */
  boolean permitsOrdinaryTransitionTo(PublicationTransactionState nextState) {
    return switch (this) {
      case PREPARED -> nextState == STAGED || nextState == BLOCKED;
      case STAGED ->
          nextState == COMMITTING
              || nextState == CLEANING
              || nextState == BLOCKED
              || nextState == CLEANUP_INCOMPLETE
              || nextState == CLEANUP_UNCERTAIN;
      case COMMITTING ->
          nextState == COMMITTED || nextState == BLOCKED || nextState == COMMIT_UNCERTAIN;
      case COMMITTED ->
          nextState == CLEANING || nextState == BLOCKED || nextState == CLEANUP_INCOMPLETE;
      case CLEANING ->
          nextState == COMPLETE
              || nextState == CLEANUP_INCOMPLETE
              || nextState == CLEANUP_UNCERTAIN;
      case COMMIT_UNCERTAIN -> nextState == COMMITTING || nextState == BLOCKED;
      case CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN -> nextState == CLEANING || nextState == BLOCKED;
      case COMPLETE, BLOCKED -> false;
    };
  }
}
