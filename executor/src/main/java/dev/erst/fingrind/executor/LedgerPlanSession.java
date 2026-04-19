package dev.erst.fingrind.executor;

/** Atomic execution seam for AI-agent-authored ledger plans. */
public interface LedgerPlanSession {
  /** Returns the administration view bound to the same atomic plan boundary. */
  BookAdministrationSession administrationSession();

  /** Returns the posting view bound to the same atomic plan boundary. */
  PostingBookSession postingSession();

  /** Returns the unified read view bound to the same atomic plan boundary. */
  BookReadSession readSession();

  /** Begins one atomic ledger-plan transaction. */
  void beginLedgerPlanTransaction();

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();
}
