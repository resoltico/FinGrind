package dev.erst.fingrind.executor.spi;

/** Owns one atomic ledger-plan transaction that cannot append or authorize a mutation. */
public interface LedgerPlanReadOnlyTransaction {
  /** Begins one read-only ledger-plan transaction bound to its immutable plan identity. */
  void beginReadOnlyLedgerPlanTransaction(String planId);

  /** Marks one source-plan step before it executes within the active plan transaction. */
  void enterLedgerPlanStep(int stepOrder);

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();
}
