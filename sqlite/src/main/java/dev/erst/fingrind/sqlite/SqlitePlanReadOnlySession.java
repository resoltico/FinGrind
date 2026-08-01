package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.LedgerPlanReadOnlyExecutionStore;

/** Public SQLite capability surface for credential-free plans over one read-only book session. */
public interface SqlitePlanReadOnlySession
    extends SqliteReadSession, LedgerPlanReadOnlyExecutionStore {
  @Override
  void close();
}
