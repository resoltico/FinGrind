package dev.erst.fingrind.contract;

/** Durable transaction mode for ledger-plan execution. */
public enum LedgerTransactionMode {
  /** Execute every step inside one durable transaction and roll back all writes on failure. */
  ATOMIC
}
