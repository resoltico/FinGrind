package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/** Statement and column operations for the SQLite FFM bridge. */
final class SqliteNativeStatements {
  private static final MemorySegment SQLITE_TRANSIENT = MemorySegment.ofAddress(-1L);

  private SqliteNativeStatements() {}

  static SqliteNativeStatement prepare(SqliteNativeDatabase database, String sql) {
    return new SqliteNativeStatement(database, sql);
  }

  static void executeScript(
      MemorySegment databaseHandle, MemorySegment sqlPointer, SqliteNativeApi sqliteApi) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment errorPointer = arena.allocate(ValueLayout.ADDRESS);
      int resultCode =
          SqliteNativeInvocation.invokeSqlite(
              "Failed to execute a SQLite script.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.ExecCall.class, sqliteApi.sqlite3Exec())
                      .invoke(
                          databaseHandle,
                          sqlPointer,
                          MemorySegment.NULL,
                          MemorySegment.NULL,
                          errorPointer));
      MemorySegment execErrorPointer = errorPointer.get(ValueLayout.ADDRESS, 0);
      try {
        if (resultCode != SqliteNativeResultCode.code("OK")) {
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
      MemorySegment databaseHandle,
      MemorySegment sql,
      MemorySegment statementPointer,
      SqliteNativeApi sqliteApi) {
    try (Arena arena = Arena.ofConfined()) {
      return SqliteNativeInvocation.invokeSqlite(
          "Failed to prepare a SQLite statement.",
          () -> {
            MemorySegment tailPointer = arena.allocate(ValueLayout.ADDRESS);
            int resultCode =
                SqliteNativeCallAdapter.adapt(
                        SqliteNativeCalls.PrepareV2Call.class, sqliteApi.sqlite3PrepareV2())
                    .invoke(databaseHandle, sql, -1, statementPointer, tailPointer);
            if (resultCode != SqliteNativeResultCode.code("OK")) {
              throw SqliteNativeErrors.failure(resultCode, databaseHandle);
            }
            return resultCode;
          });
    }
  }

  static void bindNull(
      MemorySegment statementHandle, int parameterIndex, SqliteNativeApi sqliteApi) {
    SqliteNativeInvocation.runSqlite(
        "Failed to bind a SQLite null parameter.",
        () -> {
          int resultCode =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressIntToIntCall.class, sqliteApi.sqlite3BindNull())
                  .invoke(statementHandle, parameterIndex);
          if (resultCode != SqliteNativeResultCode.code("OK")) {
            throw new SqliteNativeException(resultCode, "Failed to bind a SQLite null parameter.");
          }
        });
  }

  static void bindInt(
      MemorySegment statementHandle, int parameterIndex, int value, SqliteNativeApi sqliteApi) {
    SqliteNativeInvocation.runSqlite(
        "Failed to bind a SQLite integer parameter.",
        () -> {
          int resultCode =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressIntIntToIntCall.class, sqliteApi.sqlite3BindInt())
                  .invoke(statementHandle, parameterIndex, value);
          if (resultCode != SqliteNativeResultCode.code("OK")) {
            throw new SqliteNativeException(
                resultCode, "Failed to bind a SQLite integer parameter.");
          }
        });
  }

  static void bindLong(
      MemorySegment statementHandle, int parameterIndex, long value, SqliteNativeApi sqliteApi) {
    SqliteNativeInvocation.runSqlite(
        "Failed to bind a SQLite integer parameter.",
        () -> {
          int resultCode =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressIntLongToIntCall.class, sqliteApi.sqlite3BindInt64())
                  .invoke(statementHandle, parameterIndex, value);
          if (resultCode != SqliteNativeResultCode.code("OK")) {
            throw new SqliteNativeException(
                resultCode, "Failed to bind a SQLite integer parameter.");
          }
        });
  }

  static void bindText(
      MemorySegment statementHandle,
      int parameterIndex,
      MemorySegment textPointer,
      int byteLength,
      SqliteNativeApi sqliteApi) {
    SqliteNativeInvocation.runSqlite(
        "Failed to bind a SQLite text parameter.",
        () -> {
          int resultCode =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.BindTextCall.class, sqliteApi.sqlite3BindText())
                  .invoke(
                      statementHandle, parameterIndex, textPointer, byteLength, SQLITE_TRANSIENT);
          if (resultCode != SqliteNativeResultCode.code("OK")) {
            throw new SqliteNativeException(resultCode, "Failed to bind a SQLite text parameter.");
          }
        });
  }

  static int step(
      MemorySegment statementHandle, MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invokeSqlite(
        "Failed to step a SQLite statement.",
        () -> {
          int resultCode =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressToIntCall.class, sqliteApi.sqlite3Step())
                  .invoke(statementHandle);
          if (resultCode == SqliteNativeResultCode.code("ROW")
              || resultCode == SqliteNativeResultCode.code("DONE")) {
            return resultCode;
          }
          int extendedResultCode = extendedErrorCode(databaseHandle, sqliteApi);
          throw SqliteNativeErrors.failure(extendedResultCode, sqliteApi);
        });
  }

  static void finalizeStatement(MemorySegment statementHandle, SqliteNativeApi sqliteApi) {
    SqliteNativeInvocation.run(
        "Failed to finalize a SQLite statement.",
        () -> {
          int resultCode =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressToIntCall.class, sqliteApi.sqlite3Finalize())
                  .invoke(statementHandle);
          if (resultCode != SqliteNativeResultCode.code("OK")) {
            throw SqliteNativeErrors.failure(resultCode, sqliteApi);
          }
        });
  }

  static @Nullable String columnText(
      MemorySegment statementHandle, int columnIndex, SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to read a SQLite text column.",
        () -> {
          MemorySegment textPointer =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressIntToAddressCall.class,
                      sqliteApi.sqlite3ColumnText())
                  .invoke(statementHandle, columnIndex);
          if (textPointer.equals(MemorySegment.NULL)) {
            return null;
          }
          int byteLength =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressIntToIntCall.class, sqliteApi.sqlite3ColumnBytes())
                  .invoke(statementHandle, columnIndex);
          byte[] encodedText = textPointer.reinterpret(byteLength).toArray(ValueLayout.JAVA_BYTE);
          return new String(encodedText, StandardCharsets.UTF_8);
        });
  }

  static int columnInt(MemorySegment statementHandle, int columnIndex, SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to read a SQLite integer column.",
        () ->
            SqliteNativeCallAdapter.adapt(
                    SqliteNativeCalls.AddressIntToIntCall.class, sqliteApi.sqlite3ColumnInt())
                .invoke(statementHandle, columnIndex));
  }

  static long columnLong(
      MemorySegment statementHandle, int columnIndex, SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to read a SQLite integer column.",
        () ->
            SqliteNativeCallAdapter.adapt(
                    SqliteNativeCalls.AddressIntToLongCall.class, sqliteApi.sqlite3ColumnInt64())
                .invoke(statementHandle, columnIndex));
  }

  static int extendedErrorCode(MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite extended error code.",
        () ->
            SqliteNativeCallAdapter.adapt(
                    SqliteNativeCalls.AddressToIntCall.class, sqliteApi.sqlite3ExtendedErrcode())
                .invoke(databaseHandle));
  }
}
