package dev.erst.fingrind.executor;

/** Atomic execution seam for AI-agent-authored ledger plans. */
public interface LedgerPlanSession
    extends BookAdministrationSession, PostingBookSession, BookQuerySession {
  /** Begins one atomic ledger-plan transaction. */
  void beginLedgerPlanTransaction();

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();
}
