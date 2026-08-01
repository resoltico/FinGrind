package dev.erst.fingrind.sqlite;

import java.nio.file.Files;
import java.util.Objects;

/** Shared session-access and transaction-entry support for close mutations. */
final class SqliteClosingMutationExecutionSupport {
  /** One mutation callback that borrows the session-owned SQLite handle without closing it. */
  @FunctionalInterface
  interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;

  SqliteClosingMutationExecutionSupport(
      SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  void requireWritableMutationSession() {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    lifecycle.transactions().mutationAdmission().requireDirectMutationPermitted();
  }

  boolean missingBookFile() {
    return Files.notExists(context.bookPath());
  }

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) {
    return lifecycle.isInitializedBook(activeDatabase);
  }

  SqliteTransactionOwnership beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase) {
    return lifecycle.transactions().transaction().beginImmediateIfNeeded(activeDatabase);
  }

  <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return Objects.requireNonNull(action, "action").run(lifecycle.database());
  }
}
