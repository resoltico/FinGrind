package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.jspecify.annotations.Nullable;

/** Statement and column operations for the SQLite FFM bridge. */
final class SqliteNativeStatements {
  private SqliteNativeStatements() {}

  static void executeScript(MemorySegment databaseHandle, MemorySegment sqlPointer)
      throws SqliteNativeException {
    executeScript(databaseHandle, sqlPointer, SqliteNativeBootstrap.api());
  }

  static void executeScript(
      MemorySegment databaseHandle, MemorySegment sqlPointer, SqliteNativeApi sqliteApi)
      throws SqliteNativeException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment errorPointer = arena.allocate(ValueLayout.ADDRESS);
      int resultCode =
          SqliteNativeInvocation.invokeSqlite(
              "Failed to execute a SQLite script.",
              () ->
                  SqliteNativeCalls.exec(sqliteApi.sqlite3Exec())
                      .invoke(
                          databaseHandle,
                          sqlPointer,
                          MemorySegment.NULL,
                          MemorySegment.NULL,
                          errorPointer));
      MemorySegment execErrorPointer = errorPointer.get(ValueLayout.ADDRESS, 0);
      try {
        if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
          throw new SqliteNativeException(
              resultCode,
              SqliteNativeErrors.scriptErrorMessage(
                  resultCode,
                  execErrorPointer,
                  sqliteApi.sqlite3Errstr(),
                  SqliteNativeBootstrap.strlen()));
        }
      } finally {
        SqliteNativeErrors.freeSqliteBuffer(execErrorPointer, sqliteApi.sqlite3Free());
      }
    }
  }

  static int prepareStatement(
      MemorySegment databaseHandle, MemorySegment sql, MemorySegment statementPointer)
      throws SqliteNativeException {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    try (Arena arena = Arena.ofConfined()) {
      return SqliteNativeInvocation.invokeSqlite(
          "Failed to prepare a SQLite statement.",
          () -> {
            MemorySegment tailPointer = arena.allocate(ValueLayout.ADDRESS);
            int resultCode =
                SqliteNativeCalls.prepareV2(sqliteApi.sqlite3PrepareV2())
                    .invoke(databaseHandle, sql, -1, statementPointer, tailPointer);
            if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
              throw SqliteNativeErrors.failure(resultCode, sqliteApi);
            }
            return resultCode;
          });
    }
  }

  static void bindNull(MemorySegment statementHandle, int parameterIndex)
      throws SqliteNativeException {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    SqliteNativeInvocation.runSqlite(
        "Failed to bind a SQLite null parameter.",
        () -> {
          int resultCode =
              SqliteNativeCalls.addressIntToInt(sqliteApi.sqlite3BindNull())
                  .invoke(statementHandle, parameterIndex);
          if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
            throw new SqliteNativeException(resultCode, "Failed to bind a SQLite null parameter.");
          }
        });
  }

  static void bindInt(MemorySegment statementHandle, int parameterIndex, int value)
      throws SqliteNativeException {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    SqliteNativeInvocation.runSqlite(
        "Failed to bind a SQLite integer parameter.",
        () -> {
          int resultCode =
              SqliteNativeCalls.addressIntIntToInt(sqliteApi.sqlite3BindInt())
                  .invoke(statementHandle, parameterIndex, value);
          if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
            throw new SqliteNativeException(
                resultCode, "Failed to bind a SQLite integer parameter.");
          }
        });
  }

  static void bindText(
      MemorySegment statementHandle, int parameterIndex, MemorySegment textPointer, int byteLength)
      throws SqliteNativeException {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    SqliteNativeInvocation.runSqlite(
        "Failed to bind a SQLite text parameter.",
        () -> {
          int resultCode =
              SqliteNativeCalls.bindText(sqliteApi.sqlite3BindText())
                  .invoke(
                      statementHandle,
                      parameterIndex,
                      textPointer,
                      byteLength,
                      SqliteNativeLibrary.sqliteTransient());
          if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
            throw new SqliteNativeException(resultCode, "Failed to bind a SQLite text parameter.");
          }
        });
  }

  static int step(MemorySegment databaseHandle, MemorySegment statementHandle)
      throws SqliteNativeException {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return SqliteNativeInvocation.invokeSqlite(
        "Failed to step a SQLite statement.",
        () -> {
          int resultCode =
              SqliteNativeCalls.addressToInt(sqliteApi.sqlite3Step()).invoke(statementHandle);
          if (resultCode == SqliteNativeLibrary.SQLITE_ROW
              || resultCode == SqliteNativeLibrary.SQLITE_DONE) {
            return resultCode;
          }
          throw SqliteNativeErrors.failure(resultCode, sqliteApi);
        });
  }

  static void finalizeStatement(MemorySegment statementHandle) {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    SqliteNativeInvocation.run(
        "Failed to finalize a SQLite statement.",
        () -> {
          SqliteNativeCalls.addressToInt(sqliteApi.sqlite3Finalize()).invoke(statementHandle);
        });
  }

  static @Nullable String columnText(MemorySegment statementHandle, int columnIndex) {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return SqliteNativeInvocation.invoke(
        "Failed to read a SQLite text column.",
        () -> {
          MemorySegment textPointer =
              SqliteNativeCalls.addressIntToAddress(sqliteApi.sqlite3ColumnText())
                  .invoke(statementHandle, columnIndex);
          if (textPointer.equals(MemorySegment.NULL)) {
            return null;
          }
          int byteLength =
              SqliteNativeCalls.addressIntToInt(sqliteApi.sqlite3ColumnBytes())
                  .invoke(statementHandle, columnIndex);
          return textPointer.reinterpret(byteLength + 1L).getString(0);
        });
  }

  static int columnInt(MemorySegment statementHandle, int columnIndex) {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return SqliteNativeInvocation.invoke(
        "Failed to read a SQLite integer column.",
        () ->
            SqliteNativeCalls.addressIntToInt(sqliteApi.sqlite3ColumnInt())
                .invoke(statementHandle, columnIndex));
  }

  static int extendedErrorCode(MemorySegment databaseHandle) {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite extended error code.",
        () ->
            SqliteNativeCalls.addressToInt(sqliteApi.sqlite3ExtendedErrcode())
                .invoke(databaseHandle));
  }
}
