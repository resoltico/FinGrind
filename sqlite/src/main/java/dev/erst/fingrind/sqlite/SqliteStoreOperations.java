package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared transaction and failure helpers for SQLite-backed store adapters. */
final class SqliteStoreOperations {
  private SqliteStoreOperations() {}

  static void rollbackQuietly(SqliteNativeDatabase activeDatabase) {
    try {
      activeDatabase.executeStatement("rollback");
    } catch (SqliteNativeException | IllegalStateException exception) {
      SqliteBestEffort.reportCleanupFailure("rolling back one SQLite transaction", exception);
    }
  }

  static void closeReopenedDatabaseQuietly(@Nullable SqliteNativeDatabase reopenedDatabase) {
    if (reopenedDatabase == null) {
      return;
    }
    try {
      reopenedDatabase.close();
    } catch (SqliteNativeException | IllegalStateException exception) {
      SqliteBestEffort.reportCleanupFailure("closing one reopened SQLite database", exception);
    }
  }

  static void commitIfOwned(
      SqliteNativeDatabase activeDatabase, SqliteTransactionOwnership transactionOwnership) {
    if (transactionOwnership == SqliteTransactionOwnership.OWNED) {
      activeDatabase.executeStatement("commit");
    }
  }

  static void rollbackIfOwned(
      SqliteNativeDatabase activeDatabase, SqliteTransactionOwnership transactionOwnership) {
    if (transactionOwnership == SqliteTransactionOwnership.OWNED) {
      rollbackQuietly(activeDatabase);
    }
  }

  static SqliteStorageFailureException sqliteFailure(
      String message, SqliteNativeException exception) {
    String detail = Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
    return new SqliteStorageFailureException(
        message + " " + exception.resultName() + ": " + detail, exception);
  }

  static Optional<ContractFailure> protectedBookVerificationFailure(
      SqliteNativeException exception) {
    if (isProtectedBookVerificationResultCode(exception.resultCode())) {
      return Optional.of(
          ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
              "FinGrind could not verify the selected protected book with the supplied passphrase source.",
              "Possible causes include the wrong secret, a damaged or truncated book file, or a protected SQLite file outside the supported FinGrind format. Confirm the intended book file and passphrase source, then rerun the intended command against that same protected book.",
              null));
    }
    return Optional.empty();
  }

  private static boolean isProtectedBookVerificationResultCode(int resultCode) {
    return switch (resultCode) {
      case SqliteNativeResultCodes.NOTADB,
          SqliteNativeResultCodes.IOERR_BADKEY,
          SqliteNativeResultCodes.IOERR_CODEC ->
          true;
      default -> false;
    };
  }

  static IllegalStateException openRuntimeFailure(SqliteNativeException exception) {
    return sqliteFailure("Failed to open SQLite book connection.", exception);
  }

  static IllegalStateException foreignBookFailure() {
    return new IllegalStateException("The selected SQLite file is not a FinGrind book.");
  }

  static IllegalStateException incompleteBookFailure() {
    return new IllegalStateException(
        "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.");
  }

  static IllegalStateException unsupportedBookVersionFailure(
      int loadedUserVersion, int expectedBookVersion) {
    return new IllegalStateException(
        "The selected FinGrind book format version "
            + loadedUserVersion
            + " is unsupported. Expected version "
            + expectedBookVersion
            + ".");
  }
}
