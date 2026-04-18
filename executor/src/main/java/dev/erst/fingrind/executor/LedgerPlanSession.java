package dev.erst.fingrind.executor;

/** Atomic execution seam for AI-agent-authored ledger plans. */
public interface LedgerPlanSession {
  /** Returns the administration view bound to the same atomic plan boundary. */
  BookAdministrationSession administrationSession();

  /** Returns the posting view bound to the same atomic plan boundary. */
  PostingBookSession postingSession();

  /** Returns the query view bound to the same atomic plan boundary. */
  BookQuerySession querySession();

  /** Begins one atomic ledger-plan transaction. */
  void beginLedgerPlanTransaction();

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();
}
