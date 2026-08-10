package dev.erst.fingrind.core;

import java.time.Instant;
import java.util.Objects;

/** Captures one force-confirmed journal transition and its known two-axis outcome. */
record PublicationTransactionTransition(
    PublicationTransactionState state, Instant recordedAt, PublicationTransactionOutcome outcome) {
  PublicationTransactionTransition {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(outcome, "outcome");
    if (state == PublicationTransactionState.COMPLETE && !outcome.successful()) {
      throw new IllegalArgumentException("COMPLETE requires a successful publication outcome.");
    }
    if (state == PublicationTransactionState.COMMIT_UNCERTAIN
        && outcome.commit() != PublicationCommitOutcome.COMMIT_UNCERTAIN) {
      throw new IllegalArgumentException("COMMIT_UNCERTAIN requires an uncertain commit outcome.");
    }
    if (state == PublicationTransactionState.CLEANUP_INCOMPLETE
        && outcome.cleanup() != PublicationCleanupOutcome.INCOMPLETE) {
      throw new IllegalArgumentException(
          "CLEANUP_INCOMPLETE requires an incomplete cleanup outcome.");
    }
    if (state == PublicationTransactionState.CLEANUP_UNCERTAIN
        && outcome.cleanup() != PublicationCleanupOutcome.UNCERTAIN) {
      throw new IllegalArgumentException(
          "CLEANUP_UNCERTAIN requires an uncertain cleanup outcome.");
    }
  }

  static PublicationTransactionTransition prepared(Instant recordedAt) {
    return new PublicationTransactionTransition(
        PublicationTransactionState.PREPARED,
        recordedAt,
        new PublicationTransactionOutcome(
            PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.COMPLETE));
  }
}
