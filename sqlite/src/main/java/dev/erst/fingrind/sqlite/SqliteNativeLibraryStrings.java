package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
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
              SqliteNativeCalls.noArgAddress(libraryVersionHandle).invoke();
          return SqliteNativeErrors.cString(versionPointer, strlenHandle);
        });
  }

  static String sqlite3MultipleCiphersVersion(
      MethodHandle versionHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite3 Multiple Ciphers library version.",
        () -> {
          MemorySegment versionPointer = SqliteNativeCalls.noArgAddress(versionHandle).invoke();
          String loadedVersion = SqliteNativeErrors.cString(versionPointer, strlenHandle);
          return loadedVersion.replace("SQLite3 Multiple Ciphers ", "").strip();
        });
  }

  static String sqliteSourceId(MethodHandle sourceIdHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite source id.",
        () -> {
          MemorySegment sourceIdPointer = SqliteNativeCalls.noArgAddress(sourceIdHandle).invoke();
          return SqliteNativeErrors.cString(sourceIdPointer, strlenHandle);
        });
  }
}
