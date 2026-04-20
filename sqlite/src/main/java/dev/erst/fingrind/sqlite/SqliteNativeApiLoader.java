package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Loads and validates the process-scoped SQLite native API bundle. */
final class SqliteNativeApiLoader {
  private static final Linker LINKER = Linker.nativeLinker();

  private SqliteNativeApiLoader() {}

  static SqliteNativeApi loadApi() {
    return loadApi(
        SqliteNativeRuntimeSupport.configuredLibraryTarget(
            System.getenv(SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE),
            System.getProperty(SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY)));
  }

  static SqliteNativeApi loadApi(SqliteLibraryTarget libraryTarget) {
    Arena libraryArena = Arena.ofShared();
    try {
      SymbolLookup lookup = SymbolLookup.libraryLookup(libraryTarget.lookupTarget(), libraryArena);
      MethodHandle sqlite3Libversion =
          downcall(lookup, "sqlite3_libversion", FunctionDescriptor.of(ValueLayout.ADDRESS));
      MethodHandle sqlite3mcVersion =
          downcall(lookup, "sqlite3mc_version", FunctionDescriptor.of(ValueLayout.ADDRESS));
      MethodHandle sqlite3CompileoptionUsed =
          downcall(
              lookup,
              "sqlite3_compileoption_used",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      String loadedVersion =
          SqliteNativeRuntimeSupport.requireSupportedVersion(
              SqliteNativeBootstrap.sqliteVersion(
                  sqlite3Libversion, SqliteNativeBootstrap.strlen()),
              libraryTarget.mode());
      String loadedSqlite3mcVersion =
          SqliteNativeRuntimeSupport.requireSupportedSqlite3mcVersion(
              SqliteNativeBootstrap.sqlite3MultipleCiphersVersion(
                  sqlite3mcVersion, SqliteNativeBootstrap.strlen()),
              libraryTarget.mode());
      SqliteNativeRuntimeSupport.requireSupportedCompileOptions(
          sqlite3CompileoptionUsed, loadedVersion, loadedSqlite3mcVersion, libraryTarget.mode());
      return new SqliteNativeApi(
          libraryArena,
          downcall(
              lookup,
              "sqlite3_open_v2",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_close_v2",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_key",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_rekey",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT)),
          downcall(lookup, "sqlite3_shutdown", FunctionDescriptor.of(ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_busy_timeout",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_extended_result_codes",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_exec",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS)),
          downcall(lookup, "sqlite3_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_prepare_v2",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_bind_null",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_bind_int",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_bind_text",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_step",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_finalize",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_column_text",
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_column_bytes",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_column_int",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_errmsg",
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_errstr",
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_extended_errcode",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          loadedVersion,
          loadedSqlite3mcVersion);
    } catch (RuntimeException | Error exception) {
      libraryArena.close();
      throw exception;
    }
  }

  static MethodHandle downcall(
      SymbolLookup lookup, String symbolName, FunctionDescriptor functionDescriptor) {
    MemorySegment symbol =
        lookup
            .find(symbolName)
            .orElseThrow(() -> new IllegalStateException("Missing SQLite symbol: " + symbolName));
    return LINKER.downcallHandle(symbol, functionDescriptor);
  }
}
