package dev.erst.fingrind.sqlite;

/** Ledger-plan wrapper that adds plan-transaction control to posting capabilities. */
final class SqlitePlanExecutionCapabilitySession extends SqlitePostingCapabilitySession
    implements SqlitePlanExecutionSession {
  SqlitePlanExecutionCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public void beginLedgerPlanTransaction() {
    store.beginLedgerPlanTransaction();
  }

  @Override
  public void commitLedgerPlanTransaction() {
    store.commitLedgerPlanTransaction();
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    store.rollbackLedgerPlanTransaction();
  }
}
