package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeAddressCalls;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
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
    return new SqliteNativeException(resultCode, failureMessage(resultName, errorString));
  }

  /** Retains an open call's SQLite-provided diagnostic when SQLite returned a database handle. */
  static SqliteNativeException failure(
      int resultCode, @Nullable MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    if (databaseHandle == null || databaseHandle.equals(MemorySegment.NULL)) {
      return failure(resultCode, sqliteApi);
    }
    String resultName = resultName(resultCode);
    String errorMessage =
        errorMessage(databaseHandle, sqliteApi.sqlite3Errmsg(), SqliteNativeBootstrap.strlen());
    return new SqliteNativeException(resultCode, failureMessage(resultName, errorMessage));
  }

  static SqliteNativeException failure(int resultCode, @Nullable MemorySegment databaseHandle) {
    String resultName = resultName(resultCode);
    String errorMessage = errorMessage(databaseHandle);
    return new SqliteNativeException(resultCode, failureMessage(resultName, errorMessage));
  }

  static String failureMessage(String resultName, String detail) {
    return detail.equals(resultName) ? resultName : resultName + ": " + detail;
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
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeAddressCalls.AddressToAddressCall.class, errorMessageHandle)
                  .invoke(databaseHandle);
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
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeAddressCalls.IntToAddressCall.class, errorStringHandle)
                  .invoke(resultCode);
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
    return SqliteNativeResultCode.resultName(resultCode);
  }

  static void freeSqliteBuffer(MemorySegment pointer, MethodHandle freeHandle) {
    if (pointer == null || pointer.equals(MemorySegment.NULL)) {
      return;
    }
    SqliteNativeInvocation.run(
        "Failed to free a SQLite-owned native buffer.",
        () ->
            SqliteNativeCallAdapter.adapt(
                    SqliteNativeAddressCalls.AddressToVoidCall.class, freeHandle)
                .invoke(pointer));
  }

  static String cString(MemorySegment cStringPointer, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read a native C string.",
        () -> {
          long byteLength =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeAddressCalls.AddressToLongCall.class, strlenHandle)
                  .invoke(cStringPointer);
          return cStringPointer.reinterpret(byteLength + 1L).getString(0);
        });
  }
}
