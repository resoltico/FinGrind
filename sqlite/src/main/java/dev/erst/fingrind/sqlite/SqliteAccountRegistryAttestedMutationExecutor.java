package dev.erst.fingrind.sqlite;

import java.util.Objects;
import java.util.function.Supplier;

/** Owns transaction admission and rollback for one attested account-registry mutation. */
final class SqliteAccountRegistryAttestedMutationExecutor {
  /** One admitted mutation that persists after the chain head is observed. */
  @FunctionalInterface
  interface AttestedMutation<T> {
    /** Runs inside the admitted transaction. */
    T run(
        SqliteNativeDatabase activeDatabase,
        SqliteAttestationEvidenceStore.ObservedHead observedHead);
  }

  private final SqliteStoreLifecycle lifecycle;

  SqliteAccountRegistryAttestedMutationExecutor(SqliteStoreLifecycle lifecycle) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  <T> T execute(
      SqliteNativeDatabase activeDatabase,
      Supplier<T> bookNotInitializedOutcome,
      String failureMessage,
      AttestedMutation<T> mutation) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
    try {
      if (!lifecycle.isInitializedBook(activeDatabase)) {
        return Objects.requireNonNull(bookNotInitializedOutcome, "bookNotInitializedOutcome").get();
      }
      SqliteAttestedWriteAdmission admission =
          lifecycle.transactions().admitAttestedWrite(activeDatabase);
      transactionOwnership = admission.transactionOwnership();
      T outcome =
          Objects.requireNonNull(mutation, "mutation")
              .run(activeDatabase, admission.observedHead());
      SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
      return outcome;
    } catch (SqliteNativeException exception) {
      SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    } catch (RuntimeException exception) {
      SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
      throw exception;
    }
  }
}
