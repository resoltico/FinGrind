package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.PostingCommitStore;

/** Public SQLite-backed bookkeeping write session for administration, reads, and posting flows. */
public interface SqlitePostingSession
    extends SqliteAdministrationSession,
        SqliteReadSession,
        PostingValidationStore,
        PostingCommitStore,
        AutoCloseable {
  @Override
  void close();
}
