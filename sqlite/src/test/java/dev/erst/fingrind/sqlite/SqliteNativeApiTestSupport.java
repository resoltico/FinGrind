package dev.erst.fingrind.sqlite;

import java.lang.invoke.MethodHandle;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Same-package helpers for building deterministic native API variants in tests. */
final class SqliteNativeApiTestSupport {
  private SqliteNativeApiTestSupport() {}

  static SqliteNativeApi withOpenV2(SqliteNativeApi base, MethodHandle sqlite3OpenV2) {
    return copy(base, sqlite3OpenV2, null, null);
  }

  static SqliteNativeApi withCloseV2(SqliteNativeApi base, MethodHandle sqlite3CloseV2) {
    return copy(base, null, sqlite3CloseV2, null);
  }

  static SqliteNativeApi withRekey(SqliteNativeApi base, MethodHandle sqlite3Rekey) {
    return copy(base, null, null, sqlite3Rekey);
  }

  static SqliteNativeApi withFormatCalls(
      SqliteNativeApi base,
      @Nullable MethodHandle sqlite3mcConfig,
      @Nullable MethodHandle sqlite3mcConfigCipher,
      @Nullable MethodHandle sqlite3mcCipherName,
      @Nullable MethodHandle sqlite3FileControl) {
    Objects.requireNonNull(base, "base");
    return new SqliteNativeApi(
        base.libraryArena(),
        base.sqlite3OpenV2(),
        base.sqlite3CloseV2(),
        base.sqlite3Key(),
        base.sqlite3Rekey(),
        base.sqlite3Shutdown(),
        base.sqlite3BusyTimeout(),
        base.sqlite3ExtendedResultCodes(),
        Objects.requireNonNullElse(sqlite3mcConfig, base.sqlite3mcConfig()),
        Objects.requireNonNullElse(sqlite3mcConfigCipher, base.sqlite3mcConfigCipher()),
        Objects.requireNonNullElse(sqlite3mcCipherName, base.sqlite3mcCipherName()),
        Objects.requireNonNullElse(sqlite3FileControl, base.sqlite3FileControl()),
        base.sqlite3Exec(),
        base.sqlite3Free(),
        base.sqlite3PrepareV2(),
        base.sqlite3BindNull(),
        base.sqlite3BindInt(),
        base.sqlite3BindInt64(),
        base.sqlite3BindText(),
        base.sqlite3Step(),
        base.sqlite3Finalize(),
        base.sqlite3ColumnText(),
        base.sqlite3ColumnBytes(),
        base.sqlite3ColumnInt(),
        base.sqlite3ColumnInt64(),
        base.sqlite3Errmsg(),
        base.sqlite3Errstr(),
        base.sqlite3ExtendedErrcode(),
        base.loadedVersion(),
        base.loadedSqlite3mcVersion(),
        base.loadedSourceId(),
        base.runtimeProvenance(),
        base.loadedLibraryPath());
  }

  private static SqliteNativeApi copy(
      SqliteNativeApi base,
      @Nullable MethodHandle sqlite3OpenV2,
      @Nullable MethodHandle sqlite3CloseV2,
      @Nullable MethodHandle sqlite3Rekey) {
    Objects.requireNonNull(base, "base");
    return new SqliteNativeApi(
        base.libraryArena(),
        Objects.requireNonNullElse(sqlite3OpenV2, base.sqlite3OpenV2()),
        Objects.requireNonNullElse(sqlite3CloseV2, base.sqlite3CloseV2()),
        base.sqlite3Key(),
        Objects.requireNonNullElse(sqlite3Rekey, base.sqlite3Rekey()),
        base.sqlite3Shutdown(),
        base.sqlite3BusyTimeout(),
        base.sqlite3ExtendedResultCodes(),
        base.sqlite3mcConfig(),
        base.sqlite3mcConfigCipher(),
        base.sqlite3mcCipherName(),
        base.sqlite3FileControl(),
        base.sqlite3Exec(),
        base.sqlite3Free(),
        base.sqlite3PrepareV2(),
        base.sqlite3BindNull(),
        base.sqlite3BindInt(),
        base.sqlite3BindInt64(),
        base.sqlite3BindText(),
        base.sqlite3Step(),
        base.sqlite3Finalize(),
        base.sqlite3ColumnText(),
        base.sqlite3ColumnBytes(),
        base.sqlite3ColumnInt(),
        base.sqlite3ColumnInt64(),
        base.sqlite3Errmsg(),
        base.sqlite3Errstr(),
        base.sqlite3ExtendedErrcode(),
        base.loadedVersion(),
        base.loadedSqlite3mcVersion(),
        base.loadedSourceId(),
        base.runtimeProvenance(),
        base.loadedLibraryPath());
  }
}
