package dev.erst.fingrind.contract;

/** Per-step execution status recorded in a ledger-plan journal. */
public enum LedgerStepStatus {
  /** The step completed successfully. */
  SUCCEEDED,
  /** The step received a deterministic domain rejection. */
  REJECTED,
  /** The step assertion evaluated false. */
  ASSERTION_FAILED
}
