package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

/** Owns SQLite native connection open, close, and rekey behavior for the FFM bridge. */
final class SqliteNativeConnections {
  private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;
  private static final String KEY_VALIDATION_QUERY = "SELECT count(*) FROM sqlite_master;";

  private SqliteNativeConnections() {}

  static SqliteNativeDatabase open(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    ContractDecision<SqliteBookPassphrase> passphraseDecision =
        SqliteBookKeyFile.loadDecision(SqliteBookAccessRules.requireKeyFile(bookAccess));
    return switch (passphraseDecision) {
      case ContractDecision.Accepted<SqliteBookPassphrase>(SqliteBookPassphrase bookPassphrase) -> {
        try (bookPassphrase) {
          yield open(
              bookAccess.bookFilePath(), bookPassphrase, SqliteNativeOpenMode.READ_WRITE_CREATE);
        }
      }
      case ContractDecision.Rejected<SqliteBookPassphrase>(var failure) ->
          throw new IllegalStateException(failure.message());
    };
  }

  static SqliteNativeDatabase open(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return open(bookPath, bookPassphrase, SqliteNativeOpenMode.READ_WRITE_CREATE);
  }

  static SqliteNativeDatabase open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteNativeOpenMode openMode) {
    return open(bookPath, bookPassphrase, openMode, SqliteNativeBootstrap.api());
  }

  static SqliteNativeDatabase open(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    Objects.requireNonNull(openMode, "openMode");
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment databasePointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment filename = arena.allocateFrom(normalizedBookPath.toString());
      int resultCode = openNativeDatabase(filename, databasePointer, openMode, sqliteApi);
      MemorySegment databaseHandle = databasePointer.get(ValueLayout.ADDRESS, 0);
      if (resultCode != SqliteNativeResultCodes.OK) {
        SqliteNativeException failure = SqliteNativeErrors.failure(resultCode, sqliteApi);
        suppressCloseFailure(databaseHandle, sqliteApi, failure);
        throw failure;
      }
      return configureOpenedDatabase(databaseHandle, bookPassphrase, sqliteApi, arena);
    }
  }

  static void close(MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    SqliteNativeInvocation.runSqlite(
        "Failed to close the SQLite native library bridge.",
        () -> {
          int resultCode =
              SqliteNativeCalls.addressToInt(sqliteApi.sqlite3CloseV2()).invoke(databaseHandle);
          if (resultCode != SqliteNativeResultCodes.OK) {
            throw SqliteNativeErrors.failure(resultCode, sqliteApi);
          }
          SqliteNativeBootstrap.recordClosedConnection();
          SqliteNativeBootstrap.shutdownIfQuiescent(
              sqliteApi.sqlite3Shutdown(), SqliteNativeBootstrap.activeConnectionCount());
        });
  }

  static void rekey(SqliteNativeDatabase database, SqliteBookPassphrase bookPassphrase) {
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    SqliteNativeApi sqliteApi = database.sqliteApi();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment keyPointer = bookPassphrase.copyToCString(arena);
      int resultCode =
          SqliteNativeInvocation.invokeSqlite(
              "Failed to rekey the FinGrind SQLite book with passphrase material from "
                  + bookPassphrase.sourceDescription()
                  + ".",
              () ->
                  SqliteNativeCalls.addressAddressIntToInt(sqliteApi.sqlite3Rekey())
                      .invoke(database.handle(), keyPointer, bookPassphrase.byteLength()));
      if (resultCode != SqliteNativeResultCodes.OK) {
        throw SqliteNativeErrors.failure(resultCode, sqliteApi);
      }
      validateConfiguredKey(database.handle(), sqliteApi);
    }
  }

  private static int openNativeDatabase(
      MemorySegment filename,
      MemorySegment databasePointer,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to open the SQLite native library bridge.",
        () ->
            SqliteNativeCalls.openV2(sqliteApi.sqlite3OpenV2())
                .invoke(filename, databasePointer, openMode.flags(), MemorySegment.NULL));
  }

  static SqliteNativeDatabase configureOpenedDatabase(
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeApi sqliteApi,
      Arena arena) {
    try {
      applyKey(databaseHandle, bookPassphrase, sqliteApi, arena);
      int timeoutResult =
          SqliteNativeInvocation.invoke(
              "Failed to open the SQLite native library bridge.",
              () ->
                  SqliteNativeCalls.addressIntToInt(sqliteApi.sqlite3BusyTimeout())
                      .invoke(databaseHandle, SQLITE_BUSY_TIMEOUT_MILLIS));
      requireOpenConfigurationSuccess(timeoutResult, sqliteApi);
      int extendedCodeResult =
          SqliteNativeInvocation.invoke(
              "Failed to open the SQLite native library bridge.",
              () ->
                  SqliteNativeCalls.addressIntToInt(sqliteApi.sqlite3ExtendedResultCodes())
                      .invoke(databaseHandle, 1));
      requireOpenConfigurationSuccess(extendedCodeResult, sqliteApi);
      validateConfiguredKey(databaseHandle, sqliteApi);
      SqliteNativeBootstrap.recordOpenedConnection();
      return new SqliteNativeDatabase(databaseHandle, sqliteApi);
    } catch (SqliteNativeException exception) {
      suppressCloseFailure(databaseHandle, sqliteApi, exception);
      throw exception;
    } catch (Error error) {
      suppressCloseFailure(databaseHandle, sqliteApi, error);
      throw error;
    } catch (RuntimeException exception) {
      suppressCloseFailure(databaseHandle, sqliteApi, exception);
      throw exception;
    }
  }

  static void requireOpenConfigurationSuccess(int resultCode, SqliteNativeApi sqliteApi) {
    if (resultCode != SqliteNativeResultCodes.OK) {
      throw SqliteNativeErrors.failure(resultCode, sqliteApi);
    }
  }

  private static void suppressCloseFailure(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi, Throwable primaryFailure) {
    if (databaseHandle.equals(MemorySegment.NULL)) {
      return;
    }
    try {
      int resultCode =
          SqliteNativeCalls.addressToInt(sqliteApi.sqlite3CloseV2()).invoke(databaseHandle);
      if (resultCode != SqliteNativeResultCodes.OK) {
        primaryFailure.addSuppressed(SqliteNativeErrors.failure(resultCode, sqliteApi));
      }
    } catch (RuntimeException exception) {
      primaryFailure.addSuppressed(exception);
    }
  }

  static void applyKey(
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeApi sqliteApi,
      Arena arena) {
    SqliteNativeInvocation.runSqlite(
        "Failed to apply the FinGrind SQLite book passphrase from "
            + bookPassphrase.sourceDescription()
            + ".",
        () -> {
          MemorySegment keyPointer = bookPassphrase.copyToCString(arena);
          int resultCode =
              SqliteNativeCalls.addressAddressIntToInt(sqliteApi.sqlite3Key())
                  .invoke(databaseHandle, keyPointer, bookPassphrase.byteLength());
          requireOpenConfigurationSuccess(resultCode, sqliteApi);
        });
  }

  private static void validateConfiguredKey(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeStatements.executeScript(
          databaseHandle, arena.allocateFrom(KEY_VALIDATION_QUERY), sqliteApi);
    }
  }
}
