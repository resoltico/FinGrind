package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import java.util.Objects;

/** Tracks one final member only after its actual link or move primitive is about to run. */
final class SqlitePairPublicationMemberAttempt {
  private ProtectedBookPairPublicationMemberState state =
      ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED;

  /** Records the irreversible boundary immediately before the final filesystem primitive. */
  void markAttempted() {
    if (state != ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED) {
      throw new IllegalStateException("A protected-book pair member was marked attempted twice.");
    }
    state = ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN;
  }

  /** Records that the final primitive returned before parent-directory durability was forced. */
  void markPublishedDurabilityUnconfirmed() {
    requireState(ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN);
    state = ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED;
  }

  /** Records that the final primitive and its parent-directory durability both completed. */
  void markPublishedDurable() {
    requireState(ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED);
    state = ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE;
  }

  ProtectedBookPairPublicationMemberState state() {
    return state;
  }

  static boolean eitherAttempted(
      SqlitePairPublicationMemberAttempt bookAttempt,
      SqlitePairPublicationMemberAttempt secretAttempt) {
    return Objects.requireNonNull(bookAttempt, "bookAttempt").state()
            != ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED
        || Objects.requireNonNull(secretAttempt, "secretAttempt").state()
            != ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED;
  }

  private void requireState(ProtectedBookPairPublicationMemberState expected) {
    if (state != expected) {
      throw new IllegalStateException(
          "A protected-book pair member cannot transition from " + state + " to a later state.");
    }
  }
}
