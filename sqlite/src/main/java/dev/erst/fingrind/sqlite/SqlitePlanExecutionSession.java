package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.LedgerPlanExecutionStore;

/** Public SQLite-backed capability surface for atomic aggregate-attested ledger-plan execution. */
public interface SqlitePlanExecutionSession extends SqliteReadSession, LedgerPlanExecutionStore {
  @Override
  void close();
}
