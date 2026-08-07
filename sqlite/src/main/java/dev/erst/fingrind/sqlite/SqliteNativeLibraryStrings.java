package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeAddressCalls;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/** Reads version and source-id strings from the loaded SQLite native library. */
final class SqliteNativeLibraryStrings {
  private SqliteNativeLibraryStrings() {}

  static String sqliteVersion(MethodHandle libraryVersionHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite library version.",
        () -> {
          MemorySegment versionPointer =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeAddressCalls.NoArgAddressCall.class, libraryVersionHandle)
                  .invoke();
          return SqliteNativeErrors.cString(versionPointer, strlenHandle);
        });
  }

  static String sqlite3MultipleCiphersVersion(
      MethodHandle versionHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite3 Multiple Ciphers library version.",
        () -> {
          MemorySegment versionPointer =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeAddressCalls.NoArgAddressCall.class, versionHandle)
                  .invoke();
          String loadedVersion = SqliteNativeErrors.cString(versionPointer, strlenHandle);
          return loadedVersion.replace("SQLite3 Multiple Ciphers ", "").strip();
        });
  }

  static String sqliteSourceId(MethodHandle sourceIdHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite source id.",
        () -> {
          MemorySegment sourceIdPointer =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeAddressCalls.NoArgAddressCall.class, sourceIdHandle)
                  .invoke();
          return SqliteNativeErrors.cString(sourceIdPointer, strlenHandle);
        });
  }
}
