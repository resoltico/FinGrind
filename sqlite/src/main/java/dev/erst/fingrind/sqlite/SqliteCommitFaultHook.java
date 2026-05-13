package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;

/** Test seam for injecting deterministic failures inside one SQLite posting commit transaction. */
@FunctionalInterface
interface SqliteCommitFaultHook {
  SqliteCommitFaultHook NONE = posting -> {};

  /** Fires after the posting header row has been inserted but before journal-line persistence. */
  void afterPostingFactInserted(CommittedPosting posting);

  /**
   * Fires after pending journal lines have been staged and validated but before they are persisted
   * into the durable journal_line table.
   */
  default void beforePersistJournalLines(CommittedPosting posting) {
    Objects.requireNonNull(posting, "posting");
  }
}
