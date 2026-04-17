package dev.erst.fingrind.contract;

/** Failure policy supported by the canonical ledger-plan executor. */
public enum LedgerFailurePolicy {
  /** Stop at the first rejected or failed step and roll back the whole plan transaction. */
  HALT_ON_FIRST_FAILURE
}
