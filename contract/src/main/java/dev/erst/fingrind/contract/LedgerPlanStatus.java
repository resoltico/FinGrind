package dev.erst.fingrind.contract;

/** Final status for one ledger-plan execution. */
public enum LedgerPlanStatus {
  /** Every step completed successfully and the atomic transaction was committed. */
  SUCCEEDED,
  /** A deterministic command rejection stopped the plan and rolled back the transaction. */
  REJECTED,
  /** A ledger assertion failed and rolled back the transaction. */
  ASSERTION_FAILED
}
