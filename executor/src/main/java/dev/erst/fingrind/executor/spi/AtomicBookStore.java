package dev.erst.fingrind.executor.spi;

/** Public atomic book-store boundary used by ledger-plan execution. */
public interface AtomicBookStore extends BookStore {
  /** Begins one atomic ledger-plan transaction. */
  void beginLedgerPlanTransaction();

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();
}
