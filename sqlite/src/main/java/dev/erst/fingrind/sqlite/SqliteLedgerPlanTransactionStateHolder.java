package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Holds the one mutable ledger-plan transaction state shared by lifecycle and execution owners. */
final class SqliteLedgerPlanTransactionStateHolder {
  private LedgerPlanTransactionState state = new NoLedgerPlanTransaction();

  LedgerPlanTransactionState current() {
    return state;
  }

  boolean active() {
    return state instanceof ActiveLedgerPlanTransaction;
  }

  ActiveLedgerPlanTransaction requireActive() {
    if (state instanceof ActiveLedgerPlanTransaction activeTransaction) {
      return activeTransaction;
    }
    throw new IllegalStateException("No ledger plan transaction is active.");
  }

  void replace(LedgerPlanTransactionState replacement) {
    state = Objects.requireNonNull(replacement, "replacement");
  }

  void reset() {
    state = new NoLedgerPlanTransaction();
  }
}
