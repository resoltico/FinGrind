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
    SqliteNativeAccessGate.requireEnabled();
    return loadApi(
        SqliteNativeRuntimePolicy.configuredLibraryTarget(
            System.getenv("FINGRIND_SQLITE_LIBRARY"),
            System.getProperty(SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY)));
  }

  static SqliteNativeApi loadApi(SqliteLibraryTarget libraryTarget) {
    return loadApi(libraryTarget, Arena.ofShared());
  }

  private static SqliteNativeApi loadApi(SqliteLibraryTarget libraryTarget, Arena libraryArena) {
    try {
      SqliteManagedLibraryIdentity.VerifiedLibrarySnapshot verifiedLibrarySnapshot =
          SqliteManagedLibraryIdentity.verifiedSnapshot(libraryTarget);
      SqliteLibraryTarget runtimeTarget = verifiedLibrarySnapshot.runtimeTarget();
      SymbolLookup lookup = libraryLookup(runtimeTarget, libraryArena);
      LoadedRuntime runtime = validateRuntime(lookup, runtimeTarget);
      SqliteNativeApiBindings bindings = SqliteNativeApiBindings.bind(lookup);
      SqliteNativeApi sqliteApi = bindings.api(libraryArena, runtime);
      SqliteProtectedBookFormatIntrospection.requireRuntimeDefaultCipherContract(sqliteApi);
      return sqliteApi;
    } catch (RuntimeException | Error exception) {
      libraryArena.close();
      throw exception;
    }
  }

  private static SymbolLookup libraryLookup(SqliteLibraryTarget libraryTarget, Arena libraryArena) {
    return SymbolLookup.libraryLookup(libraryTarget.lookupTarget(), libraryArena);
  }

  private static LoadedRuntime validateRuntime(
      SymbolLookup lookup, SqliteLibraryTarget libraryTarget) {
    MethodHandle sqlite3Libversion =
        downcall(lookup, "sqlite3_libversion", FunctionDescriptor.of(ValueLayout.ADDRESS));
    MethodHandle sqlite3mcVersion =
        downcall(lookup, "sqlite3mc_version", FunctionDescriptor.of(ValueLayout.ADDRESS));
    MethodHandle sqlite3SourceId =
        downcall(lookup, "sqlite3_sourceid", FunctionDescriptor.of(ValueLayout.ADDRESS));
    MethodHandle sqlite3CompileoptionUsed =
        downcall(
            lookup,
            "sqlite3_compileoption_used",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    String loadedSqlite3mcVersion =
        SqliteNativeBootstrap.sqlite3MultipleCiphersVersion(
            sqlite3mcVersion, SqliteNativeBootstrap.strlen());
    String loadedSourceId =
        SqliteNativeBootstrap.sqliteSourceId(sqlite3SourceId, SqliteNativeBootstrap.strlen());
    String loadedVersion =
        SqliteNativeRuntimePolicy.requireSupportedVersion(
            SqliteNativeBootstrap.sqliteVersion(sqlite3Libversion, SqliteNativeBootstrap.strlen()),
            libraryTarget.mode(),
            loadedSqlite3mcVersion,
            loadedSourceId);
    SqliteNativeRuntimePolicy.requireSupportedSqlite3mcVersion(
        loadedSqlite3mcVersion, libraryTarget.mode(), loadedVersion, loadedSourceId);
    SqliteNativeRuntimePolicy.requireSupportedSourceId(
        loadedSourceId, libraryTarget.mode(), loadedVersion, loadedSqlite3mcVersion);
    SqliteNativeRuntimePolicy.requireSupportedCompileOptions(
        sqlite3CompileoptionUsed,
        loadedVersion,
        loadedSqlite3mcVersion,
        loadedSourceId,
        libraryTarget.mode());
    return new LoadedRuntime(
        loadedVersion,
        loadedSqlite3mcVersion,
        loadedSourceId,
        libraryTarget.provenance(),
        libraryTarget.lookupTarget());
  }

  static MethodHandle downcall(
      SymbolLookup lookup, String symbolName, FunctionDescriptor functionDescriptor) {
    MemorySegment symbol =
        lookup
            .find(symbolName)
            .orElseThrow(() -> new IllegalStateException("Missing SQLite symbol: " + symbolName));
    return LINKER.downcallHandle(symbol, functionDescriptor);
  }

  private record LoadedRuntime(
      String loadedVersion,
      String loadedSqlite3mcVersion,
      String loadedSourceId,
      dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance runtimeProvenance,
      String loadedLibraryPath) {}

  private record SqliteNativeApiBindings(
      SqliteConnectionCalls connections,
      SqliteFormatCalls formatCalls,
      SqliteStatementCalls statements,
      SqliteErrorCalls errors,
      MethodHandle sqlite3Shutdown) {
    private static SqliteNativeApiBindings bind(SymbolLookup lookup) {
      return new SqliteNativeApiBindings(
          SqliteConnectionCalls.bind(lookup),
          SqliteFormatCalls.bind(lookup),
          SqliteStatementCalls.bind(lookup),
          SqliteErrorCalls.bind(lookup),
          downcall(lookup, "sqlite3_shutdown", FunctionDescriptor.of(ValueLayout.JAVA_INT)));
    }

    private SqliteNativeApi api(Arena libraryArena, LoadedRuntime runtime) {
      return new SqliteNativeApi(
          libraryArena,
          connections.sqlite3OpenV2(),
          connections.sqlite3CloseV2(),
          connections.sqlite3Key(),
          connections.sqlite3Rekey(),
          sqlite3Shutdown,
          connections.sqlite3BusyTimeout(),
          connections.sqlite3ExtendedResultCodes(),
          formatCalls.sqlite3mcConfig(),
          formatCalls.sqlite3mcConfigCipher(),
          formatCalls.sqlite3mcCipherName(),
          formatCalls.sqlite3FileControl(),
          connections.sqlite3Exec(),
          connections.sqlite3Free(),
          statements.sqlite3PrepareV2(),
          statements.sqlite3BindNull(),
          statements.sqlite3BindInt(),
          statements.sqlite3BindInt64(),
          statements.sqlite3BindText(),
          statements.sqlite3Step(),
          statements.sqlite3Finalize(),
          statements.sqlite3ColumnText(),
          statements.sqlite3ColumnBytes(),
          statements.sqlite3ColumnInt(),
          statements.sqlite3ColumnInt64(),
          errors.sqlite3Errmsg(),
          errors.sqlite3Errstr(),
          errors.sqlite3ExtendedErrcode(),
          runtime.loadedVersion(),
          runtime.loadedSqlite3mcVersion(),
          runtime.loadedSourceId(),
          runtime.runtimeProvenance(),
          runtime.loadedLibraryPath());
    }
  }

  private record SqliteFormatCalls(
      MethodHandle sqlite3mcConfig,
      MethodHandle sqlite3mcConfigCipher,
      MethodHandle sqlite3mcCipherName,
      MethodHandle sqlite3FileControl) {
    private static SqliteFormatCalls bind(SymbolLookup lookup) {
      return new SqliteFormatCalls(
          downcall(
              lookup,
              "sqlite3mc_config",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3mc_config_cipher",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3mc_cipher_name",
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_file_control",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS)));
    }
  }

  private record SqliteConnectionCalls(
      MethodHandle sqlite3OpenV2,
      MethodHandle sqlite3CloseV2,
      MethodHandle sqlite3Key,
      MethodHandle sqlite3Rekey,
      MethodHandle sqlite3BusyTimeout,
      MethodHandle sqlite3ExtendedResultCodes,
      MethodHandle sqlite3Exec,
      MethodHandle sqlite3Free) {
    private static SqliteConnectionCalls bind(SymbolLookup lookup) {
      return new SqliteConnectionCalls(
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
          downcall(lookup, "sqlite3_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)));
    }
  }

  private record SqliteStatementCalls(
      MethodHandle sqlite3PrepareV2,
      MethodHandle sqlite3BindNull,
      MethodHandle sqlite3BindInt,
      MethodHandle sqlite3BindInt64,
      MethodHandle sqlite3BindText,
      MethodHandle sqlite3Step,
      MethodHandle sqlite3Finalize,
      MethodHandle sqlite3ColumnText,
      MethodHandle sqlite3ColumnBytes,
      MethodHandle sqlite3ColumnInt,
      MethodHandle sqlite3ColumnInt64) {
    private static SqliteStatementCalls bind(SymbolLookup lookup) {
      return new SqliteStatementCalls(
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
              "sqlite3_bind_int64",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_LONG)),
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
              "sqlite3_column_int64",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)));
    }
  }

  private record SqliteErrorCalls(
      MethodHandle sqlite3Errmsg, MethodHandle sqlite3Errstr, MethodHandle sqlite3ExtendedErrcode) {
    private static SqliteErrorCalls bind(SymbolLookup lookup) {
      return new SqliteErrorCalls(
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
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)));
    }
  }
}
