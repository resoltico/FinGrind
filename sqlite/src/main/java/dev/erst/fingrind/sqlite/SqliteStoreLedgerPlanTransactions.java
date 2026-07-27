package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Wires the distinct transaction, mutation-admission, and execution controls of one plan store. */
final class SqliteStoreLedgerPlanTransactions {
  private final SqliteLedgerPlanTransactionLifecycle transactionLifecycle;
  private final SqliteStoreLedgerPlanTransactionControl transactionControl;
  private final SqliteStoreLedgerPlanMutationAdmission mutationAdmission;
  private final SqliteStoreLedgerPlanExecutionControl executionControl;

  SqliteStoreLedgerPlanTransactions(SqliteStoreLifecycle lifecycle, SqliteStoreContext context) {
    Objects.requireNonNull(lifecycle, "lifecycle");
    Objects.requireNonNull(context, "context");
    SqliteLedgerPlanTransactionStateHolder transactionState =
        new SqliteLedgerPlanTransactionStateHolder();
    transactionLifecycle = new SqliteLedgerPlanTransactionLifecycle(context, transactionState);
    SqliteLedgerPlanExecution planExecution = new SqliteLedgerPlanExecution(transactionState);
    transactionControl =
        new SqliteStoreLedgerPlanTransactionControl(lifecycle, transactionLifecycle, planExecution);
    mutationAdmission =
        new SqliteStoreLedgerPlanMutationAdmission(lifecycle, transactionLifecycle, planExecution);
    executionControl = new SqliteStoreLedgerPlanExecutionControl(lifecycle, planExecution);
  }

  SqliteStoreLedgerPlanTransactionControl transaction() {
    return transactionControl;
  }

  SqliteStoreLedgerPlanMutationAdmission mutationAdmission() {
    return mutationAdmission;
  }

  SqliteStoreLedgerPlanExecutionControl execution() {
    return executionControl;
  }

  void reset() {
    transactionLifecycle.reset();
  }
}
