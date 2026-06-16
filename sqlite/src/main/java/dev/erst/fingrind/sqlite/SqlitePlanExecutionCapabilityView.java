package dev.erst.fingrind.sqlite;

/** Shared ledger-plan transaction defaults for SQLite capability wrappers. */
interface SqlitePlanExecutionCapabilityView
    extends SqlitePlanExecutionSession, SqlitePostingCapabilityView {
  @Override
  default void beginLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().begin();
  }

  @Override
  default void commitLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().commit();
  }

  @Override
  default void rollbackLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().rollback();
  }
}
