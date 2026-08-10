package dev.erst.fingrind.core;

/** Durable states of one authenticated publication transaction. */
public enum PublicationTransactionState {
  PREPARED,
  STAGED,
  COMMITTING,
  COMMITTED,
  CLEANING,
  COMPLETE,
  BLOCKED,
  COMMIT_UNCERTAIN,
  CLEANUP_INCOMPLETE,
  CLEANUP_UNCERTAIN;

  /** Returns whether recovery rather than normal forward publication is required. */
  public boolean requiresRecovery() {
    return switch (this) {
      case BLOCKED, COMMIT_UNCERTAIN, CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN -> true;
      case PREPARED, STAGED, COMMITTING, COMMITTED, CLEANING, COMPLETE -> false;
    };
  }

  /** Returns whether this state prevents any further ordinary state transition. */
  public boolean terminal() {
    return switch (this) {
      case COMPLETE, BLOCKED, COMMIT_UNCERTAIN, CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN -> true;
      case PREPARED, STAGED, COMMITTING, COMMITTED, CLEANING -> false;
    };
  }
}
