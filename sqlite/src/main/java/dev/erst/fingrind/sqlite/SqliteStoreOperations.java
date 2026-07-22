package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/** Shared transaction and failure helpers for SQLite-backed store adapters. */
final class SqliteStoreOperations {
  private static final int TRANSIENT_LOCK_MAX_ATTEMPTS = 8;
  private static final long TRANSIENT_LOCK_RETRY_BASE_MILLIS = 50L;

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

  static IllegalStateException sqliteFailure(String message, SqliteNativeException exception) {
    String resultName = exception.resultName();
    if (SqliteNativeResultCode.matchesAny(exception.resultCode(), "CONSTRAINT_CHECK")) {
      return new SqlitePersistenceInvariantException(
          message + " An upstream invariant should have rejected this request before commit.",
          exception);
    }
    String detail =
        normalizedNativeDetail(
            Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure."),
            resultName);
    return new SqliteStorageFailureException(message + " " + resultName + ": " + detail, exception);
  }

  static <T> T retryTransientLockFailures(SqliteNativeWork<T> work) {
    Objects.requireNonNull(work, "work");
    int attempt = 0;
    while (true) {
      try {
        return work.run();
      } catch (SqliteNativeException exception) {
        if (!isTransientLockFailure(exception) || attempt >= TRANSIENT_LOCK_MAX_ATTEMPTS - 1) {
          throw exception;
        }
        pauseBeforeTransientLockRetry(attempt + 1);
        attempt++;
      }
    }
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
    return SqliteNativeResultCode.matchesAny(
        resultCode,
        "NOTADB",
        "IOERR_BADKEY",
        "IOERR_CODEC",
        "CORRUPT",
        "CORRUPT_VTAB",
        "CORRUPT_SEQUENCE",
        "CORRUPT_INDEX",
        "IOERR_SHORT_READ",
        "IOERR_DATA",
        "IOERR_CORRUPTFS");
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

  private static boolean isTransientLockFailure(SqliteNativeException exception) {
    return SqliteNativeResultCode.matchesAny(
        Objects.requireNonNull(exception, "exception").resultCode(),
        "BUSY",
        "BUSY_RECOVERY",
        "BUSY_SNAPSHOT",
        "BUSY_TIMEOUT",
        "LOCKED",
        "LOCKED_SHAREDCACHE",
        "READONLY_CANTLOCK",
        "IOERR_BLOCKED",
        "IOERR_CHECKRESERVEDLOCK",
        "IOERR_LOCK",
        "IOERR_RDLOCK",
        "IOERR_SHMLOCK",
        "IOERR_UNLOCK");
  }

  private static void pauseBeforeTransientLockRetry(int attemptNumber) {
    try {
      TimeUnit.MILLISECONDS.sleep(TRANSIENT_LOCK_RETRY_BASE_MILLIS * attemptNumber);
    } catch (InterruptedException exception) {
      throw new IllegalStateException(
          "Interrupted while retrying one transient SQLite lock failure.", exception);
    }
  }

  /** One SQLite-native unit of work that participates in transient-lock retry handling. */
  @FunctionalInterface
  interface SqliteNativeWork<T> {
    /** Executes one SQLite-native unit of work for transient-lock retry handling. */
    T run();
  }

  private static String normalizedNativeDetail(String detail, String resultName) {
    String normalizedDetail = detail.strip();
    String resultPrefix = resultName + ":";
    if (normalizedDetail.startsWith(resultPrefix)) {
      normalizedDetail = normalizedDetail.substring(resultPrefix.length()).strip();
    }
    return normalizedDetail.isEmpty() ? "SQLite native failure." : normalizedDetail;
  }
}
