package dev.erst.fingrind.core;

/** The independently reported commit outcome of a publication transaction. */
public enum PublicationCommitOutcome {
  NONE_COMMITTED,
  ALL_COMMITTED,
  PARTIALLY_COMMITTED,
  COMMIT_UNCERTAIN
}
