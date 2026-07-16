package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Binds the process-scoped SQLite native call table from one verified symbol lookup. */
final class SqliteNativeApiBindings {
  private static final Linker LINKER = Linker.nativeLinker();

  private final SqliteConnectionCalls connections;
  private final SqliteFormatCalls formatCalls;
  private final SqliteStatementCalls statements;
  private final SqliteErrorCalls errors;
  private final SqliteBackupCalls backups;
  private final MethodHandle sqlite3Shutdown;

  private SqliteNativeApiBindings(
      SqliteConnectionCalls connections,
      SqliteFormatCalls formatCalls,
      SqliteStatementCalls statements,
      SqliteErrorCalls errors,
      SqliteBackupCalls backups,
      MethodHandle sqlite3Shutdown) {
    this.connections = connections;
    this.formatCalls = formatCalls;
    this.statements = statements;
    this.errors = errors;
    this.backups = backups;
    this.sqlite3Shutdown = sqlite3Shutdown;
  }

  static SqliteNativeApiBindings bind(SymbolLookup lookup) {
    return new SqliteNativeApiBindings(
        SqliteConnectionCalls.bind(lookup),
        SqliteFormatCalls.bind(lookup),
        SqliteStatementCalls.bind(lookup),
        SqliteErrorCalls.bind(lookup),
        SqliteBackupCalls.bind(lookup),
        downcall(lookup, "sqlite3_shutdown", FunctionDescriptor.of(ValueLayout.JAVA_INT)));
  }

  static MethodHandle downcall(
      SymbolLookup lookup, String symbolName, FunctionDescriptor functionDescriptor) {
    MemorySegment symbol =
        lookup
            .find(symbolName)
            .orElseThrow(() -> new IllegalStateException("Missing SQLite symbol: " + symbolName));
    return LINKER.downcallHandle(symbol, functionDescriptor);
  }

  SqliteNativeApi api(Arena libraryArena, SqliteNativeApiLoader.LoadedRuntime runtime) {
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
        runtime.loadedLibraryPath(),
        backups.sqlite3BackupInit(),
        backups.sqlite3BackupStep(),
        backups.sqlite3BackupFinish());
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

  private record SqliteBackupCalls(
      MethodHandle sqlite3BackupInit,
      MethodHandle sqlite3BackupStep,
      MethodHandle sqlite3BackupFinish) {
    private static SqliteBackupCalls bind(SymbolLookup lookup) {
      return new SqliteBackupCalls(
          downcall(
              lookup,
              "sqlite3_backup_init",
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_backup_step",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_backup_finish",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)));
    }
  }
}
