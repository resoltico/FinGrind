package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.ContractFailure;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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
      SqliteBestEffort.ignore(exception);
    }
  }

  static void closeReopenedDatabaseQuietly(@Nullable SqliteNativeDatabase reopenedDatabase) {
    if (reopenedDatabase == null) {
      return;
    }
    try {
      reopenedDatabase.close();
    } catch (SqliteNativeException | IllegalStateException exception) {
      SqliteBestEffort.ignore(exception);
    }
  }

  static void closeReopenedDatabaseQuietly(@Nullable SqliteSessionDatabase reopenedDatabase) {
    closeReopenedDatabaseQuietly(
        reopenedDatabase == null ? null : reopenedDatabase.nativeDatabase());
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

  static Optional<ContractFailure> authenticationFailure(SqliteNativeException exception) {
    if (exception.resultCode() == SqliteNativeResultCodes.NOTADB) {
      return Optional.of(
          ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED.failure(
              "FinGrind could not authenticate the selected protected book with the supplied passphrase source.",
              "Inspect the selected book file and passphrase source, then rerun with the correct secret or use "
                  + ProtocolCatalog.operationName(OperationId.INSPECT_BOOK)
                  + "/"
                  + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                  + " against the intended FinGrind book file.",
              null));
    }
    return Optional.empty();
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

  static ContractDecision<SqliteBookPassphrase> passphraseFor(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return SqliteBookKeyFile.loadDecision(SqliteBookAccessRules.requireKeyFile(bookAccess));
  }
}
