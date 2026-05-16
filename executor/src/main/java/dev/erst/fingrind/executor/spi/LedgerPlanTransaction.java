package dev.erst.fingrind.executor.spi;

/** Owns one atomic ledger-plan transaction boundary. */
public interface LedgerPlanTransaction {
  /** Begins one atomic ledger-plan transaction. */
  void beginLedgerPlanTransaction();

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();
}
