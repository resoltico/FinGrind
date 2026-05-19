package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;

/** Public SQLite-backed session for atomic ledger-plan execution workflows. */
public interface SqlitePlanExecutionSession
    extends SqlitePostingSession, LedgerPlanTransaction, AutoCloseable {
  @Override
  void close();
}
