package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Owns SQLite book rekey orchestration, reopened publication, and rollback recovery.
 *
 * <p>This service preserves the pre-rekey book when publication fails after SQLite has already
 * applied the replacement key.
 */
final class SqliteRekeyService {
  private static final String CATASTROPHIC_REKEY_RESTORE_FAILURE_MESSAGE =
      String.join(
          " ",
          "Failed to verify the rekeyed SQLite book, and FinGrind could not restore the",
          "pre-rekey book automatically.",
          "Use the preserved rollback copy in the reported storage failure to recover",
          "manually.");

  private static final String RESTORED_ORIGINAL_BOOK_FAILURE_MESSAGE =
      String.join(
          " ",
          "Failed to verify the rekeyed SQLite book. FinGrind restored the pre-rekey book",
          "on disk;",
          "reopen the session with the original passphrase and retry.");

  private static final String SQLITE_REKEY_FAILURE_MESSAGE = "Failed to rekey SQLite book.";

  /**
   * One runtime action whose failure must never displace the primary rekey outcome.
   *
   * <p>These actions run only during rollback, cleanup, or recovery after a more important failure
   * already exists.
   */
  @FunctionalInterface
  interface BestEffortRuntimeAction {
    /** Runs one best-effort runtime action. */
    void run();
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;

  SqliteRekeyService(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context);
    this.lifecycle = Objects.requireNonNull(lifecycle);
  }

  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase, Instant rekeyedAt) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    try (SqliteOwnedPassphrase activeReplacementPassphrase =
        new SqliteOwnedPassphrase(Objects.requireNonNull(replacementPassphrase))) {
      if (Files.notExists(context.bookPath())) {
        return new RekeyBookResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection
                .BookNotInitialized());
      }
      SqliteNativeDatabase activeDatabase = lifecycle.database();
      if (!lifecycle.isInitializedBook(activeDatabase)) {
        return new RekeyBookResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection
                .BookNotInitialized());
      }
      SqliteRekeyRollbackFile rollbackFile = SqliteRekeyRollbackFile.create(context.bookPath());
      SqliteNativeConnections.rekey(activeDatabase, activeReplacementPassphrase.nativePassphrase());
      try {
        return publishRekeyedDatabase(
            activeDatabase,
            activeReplacementPassphrase.nativePassphrase(),
            rollbackFile,
            rekeyedAt);
      } catch (RuntimeException exception) {
        RuntimeException closeFailure =
            captureBestEffortRuntimeFailure(
                () -> SqliteStoreContext.closeOwnedDatabase(activeDatabase));
        lifecycle.clearDatabaseState();
        RuntimeException restoreFailure =
            captureBestEffortRuntimeFailure(() -> rollbackFile.restore(context.bookPath()));
        throw finalizeFailedRekey(
            exception, restoreFailure, closeFailure, rollbackFile::deleteQuietly);
      }
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(SQLITE_REKEY_FAILURE_MESSAGE, exception);
    }
  }

  RekeyBookResult publishRekeyedDatabase(
      SqliteNativeDatabase activeDatabase,
      SqliteBookPassphrase replacementPassphrase,
      SqliteRekeyRollbackFile rollbackFile,
      Instant rekeyedAt) {
    SqliteNativeDatabase reopenedDatabase =
        context.openConfiguredDatabaseWithoutRollbackArtifactWarning(replacementPassphrase);
    boolean published = false;
    try {
      lifecycle.requireInitializedBook(reopenedDatabase);
      SqliteTransactionOwnership transactionOwnership =
          lifecycle.beginImmediateIfNeeded(reopenedDatabase);
      try {
        SqliteAuditEventWriter.insertAuditEvent(
            reopenedDatabase, BookAuditEvent.bookRekeyed(rekeyedAt));
        SqliteStoreOperations.commitIfOwned(reopenedDatabase, transactionOwnership);
      } catch (RuntimeException exception) {
        SqliteStoreOperations.rollbackIfOwned(reopenedDatabase, transactionOwnership);
        throw exception;
      }
      SqliteStoreContext.closeOwnedDatabase(activeDatabase);
      lifecycle.clearDatabaseState();
      lifecycle.publishDatabase(reopenedDatabase);
      lifecycle.rotateSessionSecret(replacementPassphrase);
      published = true;
      rollbackFile.deleteQuietly();
      return new RekeyBookResult.Rekeyed(context.bookPath());
    } finally {
      if (!published) {
        SqliteStoreOperations.closeReopenedDatabaseQuietly(reopenedDatabase);
      }
    }
  }

  static @Nullable RuntimeException captureBestEffortRuntimeFailure(
      BestEffortRuntimeAction action) {
    try {
      action.run();
      return null;
    } catch (RuntimeException failure) {
      return failure;
    }
  }

  static IllegalStateException catastrophicRekeyRestoreFailure(
      RuntimeException verificationFailure,
      RuntimeException restoreFailure,
      @Nullable RuntimeException closeFailure) {
    IllegalStateException catastrophicFailure =
        new IllegalStateException(CATASTROPHIC_REKEY_RESTORE_FAILURE_MESSAGE, verificationFailure);
    catastrophicFailure.addSuppressed(restoreFailure);
    if (closeFailure != null) {
      catastrophicFailure.addSuppressed(closeFailure);
    }
    return catastrophicFailure;
  }

  static IllegalStateException finalizeFailedRekey(
      RuntimeException verificationFailure,
      @Nullable RuntimeException restoreFailure,
      @Nullable RuntimeException closeFailure,
      BestEffortRuntimeAction rollbackDeleteAction) {
    Objects.requireNonNull(rollbackDeleteAction);
    if (restoreFailure != null) {
      return catastrophicRekeyRestoreFailure(verificationFailure, restoreFailure, closeFailure);
    }
    rollbackDeleteAction.run();
    return restoredOriginalBookFailure(verificationFailure, closeFailure);
  }

  static IllegalStateException restoredOriginalBookFailure(
      RuntimeException verificationFailure, @Nullable RuntimeException closeFailure) {
    Objects.requireNonNull(verificationFailure);
    if (closeFailure != null) {
      verificationFailure.addSuppressed(closeFailure);
    }
    return new IllegalStateException(RESTORED_ORIGINAL_BOOK_FAILURE_MESSAGE, verificationFailure);
  }
}
