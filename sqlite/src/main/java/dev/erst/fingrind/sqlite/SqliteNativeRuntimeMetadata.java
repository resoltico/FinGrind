package dev.erst.fingrind.sqlite;

import java.lang.invoke.MethodHandle;

/** Reads and exposes the published SQLite native runtime version metadata. */
final class SqliteNativeRuntimeMetadata {
  private SqliteNativeRuntimeMetadata() {}

  static String sqliteVersion() {
    return SqliteNativeBootstrap.api().loadedVersion();
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle) {
    return sqliteVersion(libraryVersionHandle, SqliteNativeBootstrap.strlen());
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle, MethodHandle strlenHandle) {
    return SqliteNativeLibraryStrings.sqliteVersion(libraryVersionHandle, strlenHandle);
  }

  static String sqlite3MultipleCiphersVersion() {
    return SqliteNativeBootstrap.api().loadedSqlite3mcVersion();
  }

  static String sqlite3MultipleCiphersVersion(MethodHandle versionHandle) {
    return sqlite3MultipleCiphersVersion(versionHandle, SqliteNativeBootstrap.strlen());
  }

  static String sqlite3MultipleCiphersVersion(
      MethodHandle versionHandle, MethodHandle strlenHandle) {
    return SqliteNativeLibraryStrings.sqlite3MultipleCiphersVersion(versionHandle, strlenHandle);
  }

  static String sqliteSourceId() {
    return SqliteNativeBootstrap.api().loadedSourceId();
  }

  static String sqliteSourceId(MethodHandle sourceIdHandle) {
    return sqliteSourceId(sourceIdHandle, SqliteNativeBootstrap.strlen());
  }

  static String sqliteSourceId(MethodHandle sourceIdHandle, MethodHandle strlenHandle) {
    return SqliteNativeLibraryStrings.sqliteSourceId(sourceIdHandle, strlenHandle);
  }
}
