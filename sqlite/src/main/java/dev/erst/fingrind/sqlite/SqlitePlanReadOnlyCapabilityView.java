package dev.erst.fingrind.sqlite;

/** Shared credential-free ledger-plan transaction defaults for SQLite read-only sessions. */
interface SqlitePlanReadOnlyCapabilityView
    extends SqlitePlanReadOnlySession, SqliteReadCapabilityView {
  @Override
  default void beginReadOnlyLedgerPlanTransaction(String planId) {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().transaction().beginReadOnlyPlan(planId);
  }

  @Override
  default void enterLedgerPlanStep(int stepOrder) {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().execution().enterPlanStep(stepOrder);
  }

  @Override
  default void commitLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().transaction().commit();
  }

  @Override
  default void rollbackLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().transaction().rollback();
  }
}
