package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.util.Objects;

/**
 * Signals that an initialization COMMIT failed to acknowledge and a fresh post-rollback read did
 * not prove the selected SQLite file blank.
 *
 * <p>The carried facts were built before COMMIT from the verified genesis append. Callers must
 * retain founder custody and expose those facts for reconciliation rather than treating this as a
 * normal failed initialization.
 */
public final class SqliteOpenBookCompletionUncertainException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final transient BookOpeningOutcome.Opened openedBook;

  /** Creates one indeterminate-completion signal with the prebuilt opening facts. */
  public SqliteOpenBookCompletionUncertainException(
      BookOpeningOutcome.Opened openedBook, RuntimeException commitFailure) {
    super(
        "SQLite did not acknowledge book initialization COMMIT and post-rollback state was not"
            + " proven blank.",
        Objects.requireNonNull(commitFailure, "commitFailure"));
    this.openedBook = Objects.requireNonNull(openedBook, "openedBook");
  }

  /** Returns the complete verified genesis facts built before the unacknowledged COMMIT. */
  public BookOpeningOutcome.Opened openedBook() {
    return openedBook;
  }
}
