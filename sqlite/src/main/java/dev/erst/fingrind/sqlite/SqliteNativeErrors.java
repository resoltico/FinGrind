package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import org.jspecify.annotations.Nullable;

/** Error/result-code/string helpers for the SQLite FFM bridge. */
final class SqliteNativeErrors {
  private SqliteNativeErrors() {}

  static SqliteNativeException failure(int resultCode, SqliteNativeApi sqliteApi) {
    String resultName = resultName(resultCode);
    String errorString =
        errorString(resultCode, sqliteApi.sqlite3Errstr(), SqliteNativeBootstrap.strlen());
    String message = errorString.equals(resultName) ? resultName : resultName + ": " + errorString;
    return new SqliteNativeException(resultCode, message);
  }

  static String errorMessage(@Nullable MemorySegment databaseHandle) {
    if (databaseHandle == null || databaseHandle.equals(MemorySegment.NULL)) {
      return "SQLite native failure.";
    }
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return errorMessage(databaseHandle, sqliteApi.sqlite3Errmsg(), SqliteNativeBootstrap.strlen());
  }

  static String errorMessage(
      @Nullable MemorySegment databaseHandle, MethodHandle errorMessageHandle) {
    return errorMessage(databaseHandle, errorMessageHandle, SqliteNativeBootstrap.strlen());
  }

  static String errorMessage(
      @Nullable MemorySegment databaseHandle,
      MethodHandle errorMessageHandle,
      MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite error message.",
        () -> {
          if (databaseHandle == null || databaseHandle.equals(MemorySegment.NULL)) {
            return "SQLite native failure.";
          }
          MemorySegment errorMessagePointer =
              SqliteNativeCalls.addressToAddress(errorMessageHandle).invoke(databaseHandle);
          if (errorMessagePointer.equals(MemorySegment.NULL)) {
            return "SQLite native failure.";
          }
          return cString(errorMessagePointer, strlenHandle);
        });
  }

  static String scriptErrorMessage(MemorySegment databaseHandle, MemorySegment execErrorPointer) {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return scriptErrorMessage(
        databaseHandle,
        execErrorPointer,
        sqliteApi.sqlite3Errmsg(),
        SqliteNativeBootstrap.strlen());
  }

  static String errorString(int resultCode) {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return errorString(resultCode, sqliteApi.sqlite3Errstr(), SqliteNativeBootstrap.strlen());
  }

  static String errorString(
      int resultCode, MethodHandle errorStringHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite error string.",
        () -> {
          MemorySegment errorStringPointer =
              SqliteNativeCalls.intToAddress(errorStringHandle).invoke(resultCode);
          if (errorStringPointer == null || errorStringPointer.equals(MemorySegment.NULL)) {
            return resultName(resultCode);
          }
          String errorString = cString(errorStringPointer, strlenHandle);
          return errorString.isBlank() ? resultName(resultCode) : errorString;
        });
  }

  static String scriptErrorMessage(
      int resultCode,
      @Nullable MemorySegment execErrorPointer,
      MethodHandle errorStringHandle,
      MethodHandle strlenHandle) {
    if (execErrorPointer != null && !execErrorPointer.equals(MemorySegment.NULL)) {
      return cString(execErrorPointer, strlenHandle);
    }
    return errorString(resultCode, errorStringHandle, strlenHandle);
  }

  static String scriptErrorMessage(
      MemorySegment databaseHandle,
      @Nullable MemorySegment execErrorPointer,
      MethodHandle errorMessageHandle,
      MethodHandle strlenHandle) {
    if (execErrorPointer != null && !execErrorPointer.equals(MemorySegment.NULL)) {
      return cString(execErrorPointer, strlenHandle);
    }
    return errorMessage(databaseHandle, errorMessageHandle, strlenHandle);
  }

  static String resultName(int resultCode) {
    return switch (resultCode) {
      case SqliteNativeResultCodes.OK -> "SQLITE_OK";
      case SqliteNativeResultCodes.ROW -> "SQLITE_ROW";
      case SqliteNativeResultCodes.DONE -> "SQLITE_DONE";
      case SqliteNativeResultCodes.CONSTRAINT_UNIQUE -> "SQLITE_CONSTRAINT_UNIQUE";
      case SqliteNativeResultCodes.CONSTRAINT_PRIMARYKEY -> "SQLITE_CONSTRAINT_PRIMARYKEY";
      case SqliteNativeResultCodes.CONSTRAINT_DATATYPE -> "SQLITE_CONSTRAINT_DATATYPE";
      case SqliteNativeResultCodes.CONSTRAINT_FOREIGNKEY -> "SQLITE_CONSTRAINT_FOREIGNKEY";
      case SqliteNativeResultCodes.CANTOPEN -> "SQLITE_CANTOPEN";
      case SqliteNativeResultCodes.CANTOPEN_ISDIR -> "SQLITE_CANTOPEN_ISDIR";
      case SqliteNativeResultCodes.NOTADB -> "SQLITE_NOTADB";
      default -> "SQLITE_" + resultCode;
    };
  }

  static void freeSqliteBuffer(MemorySegment pointer, MethodHandle freeHandle) {
    if (pointer == null || pointer.equals(MemorySegment.NULL)) {
      return;
    }
    SqliteNativeInvocation.run(
        "Failed to free a SQLite-owned native buffer.",
        () -> {
          SqliteNativeCalls.addressToVoid(freeHandle).invoke(pointer);
        });
  }

  static String cString(MemorySegment cStringPointer, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read a native C string.",
        () -> {
          long byteLength = SqliteNativeCalls.addressToLong(strlenHandle).invoke(cStringPointer);
          return cStringPointer.reinterpret(byteLength + 1L).getString(0);
        });
  }
}
