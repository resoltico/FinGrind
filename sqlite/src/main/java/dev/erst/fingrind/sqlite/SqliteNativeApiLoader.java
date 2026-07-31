package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.function.Consumer;

/** Loads and validates the process-scoped SQLite native API bundle. */
final class SqliteNativeApiLoader {
  private SqliteNativeApiLoader() {}

  static SqliteNativeApi loadApi(Consumer<SqliteVerifiedLibrarySnapshot> verifiedSnapshotConsumer) {
    SqliteNativeAccessGate.requireEnabled();
    return loadApi(
        SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
            System.getProperty(SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY)),
        verifiedSnapshotConsumer);
  }

  static SqliteNativeApi loadApi(
      SqliteLibraryTarget libraryTarget,
      Consumer<SqliteVerifiedLibrarySnapshot> verifiedSnapshotConsumer) {
    return loadApi(libraryTarget, Arena.ofShared(), verifiedSnapshotConsumer);
  }

  private static SqliteNativeApi loadApi(
      SqliteLibraryTarget libraryTarget,
      Arena libraryArena,
      Consumer<SqliteVerifiedLibrarySnapshot> verifiedSnapshotConsumer) {
    try {
      SqliteVerifiedLibrarySnapshot verifiedLibrarySnapshot =
          SqliteManagedLibraryIdentity.verifiedSnapshot(libraryTarget);
      verifiedLibrarySnapshot.requireCurrentBytesMatchVerifiedDigestBeforePathLoad();
      SqliteLibraryTarget runtimeTarget = verifiedLibrarySnapshot.runtimeTarget();
      SymbolLookup lookup = libraryLookup(runtimeTarget, libraryArena);
      LoadedRuntime runtime = validateRuntime(lookup, runtimeTarget);
      SqliteNativeVfs.requireCurrentHostVfsAvailable(lookup);
      SqliteNativeApiBindings bindings = SqliteNativeApiBindings.bind(lookup);
      SqliteNativeApi sqliteApi = bindings.api(libraryArena, runtime);
      SqliteProtectedBookFormatIntrospection.requireRuntimeDefaultCipherContract(sqliteApi);
      Objects.requireNonNull(verifiedSnapshotConsumer, "verifiedSnapshotConsumer")
          .accept(verifiedLibrarySnapshot);
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
        SqliteNativeApiBindings.downcall(
            lookup, "sqlite3_libversion", FunctionDescriptor.of(ValueLayout.ADDRESS));
    MethodHandle sqlite3mcVersion =
        SqliteNativeApiBindings.downcall(
            lookup, "sqlite3mc_version", FunctionDescriptor.of(ValueLayout.ADDRESS));
    MethodHandle sqlite3SourceId =
        SqliteNativeApiBindings.downcall(
            lookup, "sqlite3_sourceid", FunctionDescriptor.of(ValueLayout.ADDRESS));
    MethodHandle sqlite3CompileoptionUsed =
        SqliteNativeApiBindings.downcall(
            lookup,
            "sqlite3_compileoption_used",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    String loadedSqlite3mcVersion =
        SqliteNativeRuntimeMetadata.sqlite3MultipleCiphersVersion(
            sqlite3mcVersion, SqliteNativeBootstrap.strlen());
    String loadedSourceId =
        SqliteNativeRuntimeMetadata.sqliteSourceId(sqlite3SourceId, SqliteNativeBootstrap.strlen());
    String loadedVersion =
        SqliteNativeCompatibilityPolicy.requireSupportedVersion(
            SqliteNativeRuntimeMetadata.sqliteVersion(
                sqlite3Libversion, SqliteNativeBootstrap.strlen()),
            libraryTarget.mode(),
            loadedSqlite3mcVersion,
            loadedSourceId);
    SqliteNativeCompatibilityPolicy.requireSupportedSqlite3mcVersion(
        loadedSqlite3mcVersion, libraryTarget.mode(), loadedVersion, loadedSourceId);
    SqliteNativeCompatibilityPolicy.requireSupportedSourceId(
        loadedSourceId, libraryTarget.mode(), loadedVersion, loadedSqlite3mcVersion);
    SqliteNativeCompatibilityPolicy.requireSupportedCompileOptions(
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

  record LoadedRuntime(
      String loadedVersion,
      String loadedSqlite3mcVersion,
      String loadedSourceId,
      dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance runtimeProvenance,
      String loadedLibraryPath) {}
}
