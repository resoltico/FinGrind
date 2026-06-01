package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/** Reads protected-book runtime and cipher facts from one opened native SQLite database. */
final class SqliteNativeProtectedBookRuntime {
  private final SqliteNativeDatabase database;

  SqliteNativeProtectedBookRuntime(SqliteNativeDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  int runtimeParameter(String parameterName) {
    try (Arena arena = Arena.ofConfined()) {
      int value =
          SqliteNativeInvocation.invoke(
              "Failed to read one SQLite3MC runtime parameter.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.AddressAddressIntToIntCall.class,
                          database.sqliteApi().sqlite3mcConfig())
                      .invoke(database.handle(), arena.allocateFrom(parameterName), -1));
      if (value < 0) {
        throw new IllegalStateException(
            "Managed SQLite runtime did not expose protected-book parameter `"
                + parameterName
                + "`.");
      }
      return value;
    }
  }

  int cipherParameter(String cipherName, String parameterName) {
    try (Arena arena = Arena.ofConfined()) {
      int value =
          SqliteNativeInvocation.invoke(
              "Failed to read one SQLite3MC cipher parameter.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.AddressAddressAddressIntToIntCall.class,
                          database.sqliteApi().sqlite3mcConfigCipher())
                      .invoke(
                          database.handle(),
                          arena.allocateFrom(cipherName),
                          arena.allocateFrom(parameterName),
                          -1));
      if (value < 0) {
        throw new IllegalStateException(
            "Managed SQLite runtime did not expose cipher parameter `"
                + parameterName
                + "` for `"
                + cipherName
                + "`.");
      }
      return value;
    }
  }

  String cipherName(int cipherIndex) {
    MemorySegment cipherNamePointer =
        SqliteNativeInvocation.invoke(
            "Failed to read the SQLite3MC cipher name.",
            () ->
                SqliteNativeCallAdapter.adapt(
                        SqliteNativeCalls.IntToAddressCall.class,
                        database.sqliteApi().sqlite3mcCipherName())
                    .invoke(cipherIndex));
    if (cipherNamePointer == null || cipherNamePointer.equals(MemorySegment.NULL)) {
      throw new IllegalStateException(
          "Managed SQLite runtime returned no cipher name for cipher index " + cipherIndex + ".");
    }
    return SqliteNativeErrors.cString(cipherNamePointer, SqliteNativeBootstrap.strlen());
  }

  int fileControlReserveBytes(int fileControlCode) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment reservedBytesPointer = arena.allocate(ValueLayout.JAVA_INT);
      int resultCode =
          SqliteNativeInvocation.invokeSqlite(
              "Failed to inspect SQLite reserve bytes.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.AddressAddressIntAddressToIntCall.class,
                          database.sqliteApi().sqlite3FileControl())
                      .invoke(
                          database.handle(),
                          MemorySegment.NULL,
                          fileControlCode,
                          reservedBytesPointer));
      if (resultCode != SqliteNativeResultCode.code("OK")) {
        throw SqliteNativeErrors.failure(resultCode, database.sqliteApi());
      }
      return reservedBytesPointer.get(ValueLayout.JAVA_INT, 0L);
    }
  }
}
