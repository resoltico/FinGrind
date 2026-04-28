package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Loaded SQLite symbol handles and process-scoped resources for one JVM lifetime. */
record SqliteNativeApi(
    Arena libraryArena,
    MethodHandle sqlite3OpenV2,
    MethodHandle sqlite3CloseV2,
    MethodHandle sqlite3Key,
    MethodHandle sqlite3Rekey,
    MethodHandle sqlite3Shutdown,
    MethodHandle sqlite3BusyTimeout,
    MethodHandle sqlite3ExtendedResultCodes,
    MethodHandle sqlite3Exec,
    MethodHandle sqlite3Free,
    MethodHandle sqlite3PrepareV2,
    MethodHandle sqlite3BindNull,
    MethodHandle sqlite3BindInt,
    MethodHandle sqlite3BindText,
    MethodHandle sqlite3Step,
    MethodHandle sqlite3Finalize,
    MethodHandle sqlite3ColumnText,
    MethodHandle sqlite3ColumnBytes,
    MethodHandle sqlite3ColumnInt,
    MethodHandle sqlite3Errmsg,
    MethodHandle sqlite3Errstr,
    MethodHandle sqlite3ExtendedErrcode,
    String loadedVersion,
    String loadedSqlite3mcVersion,
    String loadedSourceId,
    SqliteRuntimeProvenance runtimeProvenance,
    String loadedLibraryPath) {
  SqliteNativeApi {
    Objects.requireNonNull(libraryArena, "libraryArena");
    Objects.requireNonNull(sqlite3OpenV2, "sqlite3OpenV2");
    Objects.requireNonNull(sqlite3CloseV2, "sqlite3CloseV2");
    Objects.requireNonNull(sqlite3Key, "sqlite3Key");
    Objects.requireNonNull(sqlite3Rekey, "sqlite3Rekey");
    Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
    Objects.requireNonNull(sqlite3BusyTimeout, "sqlite3BusyTimeout");
    Objects.requireNonNull(sqlite3ExtendedResultCodes, "sqlite3ExtendedResultCodes");
    Objects.requireNonNull(sqlite3Exec, "sqlite3Exec");
    Objects.requireNonNull(sqlite3Free, "sqlite3Free");
    Objects.requireNonNull(sqlite3PrepareV2, "sqlite3PrepareV2");
    Objects.requireNonNull(sqlite3BindNull, "sqlite3BindNull");
    Objects.requireNonNull(sqlite3BindInt, "sqlite3BindInt");
    Objects.requireNonNull(sqlite3BindText, "sqlite3BindText");
    Objects.requireNonNull(sqlite3Step, "sqlite3Step");
    Objects.requireNonNull(sqlite3Finalize, "sqlite3Finalize");
    Objects.requireNonNull(sqlite3ColumnText, "sqlite3ColumnText");
    Objects.requireNonNull(sqlite3ColumnBytes, "sqlite3ColumnBytes");
    Objects.requireNonNull(sqlite3ColumnInt, "sqlite3ColumnInt");
    Objects.requireNonNull(sqlite3Errmsg, "sqlite3Errmsg");
    Objects.requireNonNull(sqlite3Errstr, "sqlite3Errstr");
    Objects.requireNonNull(sqlite3ExtendedErrcode, "sqlite3ExtendedErrcode");
    Objects.requireNonNull(loadedVersion, "loadedVersion");
    Objects.requireNonNull(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    Objects.requireNonNull(loadedSourceId, "loadedSourceId");
    Objects.requireNonNull(runtimeProvenance, "runtimeProvenance");
    Objects.requireNonNull(loadedLibraryPath, "loadedLibraryPath");
    loadedVersion = loadedVersion.strip();
    loadedSqlite3mcVersion = loadedSqlite3mcVersion.strip();
    loadedSourceId = loadedSourceId.strip();
    loadedLibraryPath = loadedLibraryPath.strip();
    if (loadedVersion.isEmpty()) {
      throw new IllegalArgumentException("loadedVersion must not be blank.");
    }
    if (loadedSqlite3mcVersion.isEmpty()) {
      throw new IllegalArgumentException("loadedSqlite3mcVersion must not be blank.");
    }
    if (loadedSourceId.isEmpty()) {
      throw new IllegalArgumentException("loadedSourceId must not be blank.");
    }
    if (loadedLibraryPath.isEmpty()) {
      throw new IllegalArgumentException("loadedLibraryPath must not be blank.");
    }
  }
}
