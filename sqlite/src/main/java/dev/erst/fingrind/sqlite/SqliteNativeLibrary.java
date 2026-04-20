package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Minimal Java FFM binding façade over the configured SQLite C library. */
final class SqliteNativeLibrary {
  static final int SQLITE_OK = 0;
  static final int SQLITE_ROW = 100;
  static final int SQLITE_DONE = 101;

  static final int SQLITE_OPEN_READONLY = 0x00000001;
  static final int SQLITE_OPEN_READWRITE = 0x00000002;
  static final int SQLITE_OPEN_CREATE = 0x00000004;

  static final int SQLITE_CONSTRAINT_UNIQUE = 2067;
  static final int SQLITE_CONSTRAINT_PRIMARYKEY = 1555;
  static final int SQLITE_CONSTRAINT_DATATYPE = 3091;
  static final int SQLITE_CONSTRAINT_FOREIGNKEY = 787;
  static final int SQLITE_CANTOPEN = 14;
  static final int SQLITE_CANTOPEN_ISDIR = 526;
  static final int SQLITE_NOTADB = 26;

  private static final MemorySegment SQLITE_TRANSIENT = MemorySegment.ofAddress(-1L);

  private SqliteNativeLibrary() {}

  static SqliteNativeDatabase open(BookAccess bookAccess) throws SqliteNativeException {
    return SqliteNativeConnections.open(bookAccess);
  }

  static SqliteNativeDatabase open(Path bookPath, SqliteBookPassphrase bookPassphrase)
      throws SqliteNativeException {
    return SqliteNativeConnections.open(bookPath, bookPassphrase);
  }

  static SqliteNativeDatabase open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteNativeOpenMode openMode)
      throws SqliteNativeException {
    return SqliteNativeConnections.open(bookPath, bookPassphrase, openMode);
  }

  static void close(MemorySegment databaseHandle) throws SqliteNativeException {
    SqliteNativeConnections.close(databaseHandle);
  }

  static void rekey(SqliteNativeDatabase database, SqliteBookPassphrase bookPassphrase)
      throws SqliteNativeException {
    SqliteNativeConnections.rekey(database, bookPassphrase);
  }

  static AutoCloseable overrideSqlite3RekeyHandleForTesting(MethodHandle sqlite3RekeyHandle) {
    return SqliteNativeConnections.overrideSqlite3RekeyHandleForTesting(sqlite3RekeyHandle);
  }

  static AutoCloseable overrideSqlite3OpenV2HandleForTesting(MethodHandle sqlite3OpenV2Handle) {
    return SqliteNativeConnections.overrideSqlite3OpenV2HandleForTesting(sqlite3OpenV2Handle);
  }

  static AutoCloseable overrideSqlite3CloseV2HandleForTesting(MethodHandle sqlite3CloseV2Handle) {
    return SqliteNativeConnections.overrideSqlite3CloseV2HandleForTesting(sqlite3CloseV2Handle);
  }

  static SqliteNativeStatement prepare(SqliteNativeDatabase database, String sql)
      throws SqliteNativeException {
    return new SqliteNativeStatement(database, sql);
  }

  static void executeScript(MemorySegment databaseHandle, MemorySegment sqlPointer)
      throws SqliteNativeException {
    SqliteNativeStatements.executeScript(databaseHandle, sqlPointer);
  }

  static int prepareStatement(
      MemorySegment databaseHandle, MemorySegment sql, MemorySegment statementPointer)
      throws SqliteNativeException {
    return SqliteNativeStatements.prepareStatement(databaseHandle, sql, statementPointer);
  }

  static void bindNull(MemorySegment statementHandle, int parameterIndex)
      throws SqliteNativeException {
    SqliteNativeStatements.bindNull(statementHandle, parameterIndex);
  }

  static void bindInt(MemorySegment statementHandle, int parameterIndex, int value)
      throws SqliteNativeException {
    SqliteNativeStatements.bindInt(statementHandle, parameterIndex, value);
  }

  static void bindText(
      MemorySegment statementHandle, int parameterIndex, MemorySegment textPointer, int byteLength)
      throws SqliteNativeException {
    SqliteNativeStatements.bindText(statementHandle, parameterIndex, textPointer, byteLength);
  }

  static int step(MemorySegment databaseHandle, MemorySegment statementHandle)
      throws SqliteNativeException {
    return SqliteNativeStatements.step(databaseHandle, statementHandle);
  }

  static void finalizeStatement(MemorySegment statementHandle) {
    SqliteNativeStatements.finalizeStatement(statementHandle);
  }

  static @Nullable String columnText(MemorySegment statementHandle, int columnIndex) {
    return SqliteNativeStatements.columnText(statementHandle, columnIndex);
  }

  static int columnInt(MemorySegment statementHandle, int columnIndex) {
    return SqliteNativeStatements.columnInt(statementHandle, columnIndex);
  }

  static int extendedErrorCode(MemorySegment databaseHandle) {
    return SqliteNativeStatements.extendedErrorCode(databaseHandle);
  }

  static String errorMessage(@Nullable MemorySegment databaseHandle) {
    return SqliteNativeErrors.errorMessage(databaseHandle);
  }

  static String errorMessage(
      @Nullable MemorySegment databaseHandle, MethodHandle errorMessageHandle) {
    return SqliteNativeErrors.errorMessage(databaseHandle, errorMessageHandle);
  }

  static String errorMessage(
      @Nullable MemorySegment databaseHandle,
      MethodHandle errorMessageHandle,
      MethodHandle strlenHandle) {
    return SqliteNativeErrors.errorMessage(databaseHandle, errorMessageHandle, strlenHandle);
  }

  static String scriptErrorMessage(MemorySegment databaseHandle, MemorySegment execErrorPointer) {
    return SqliteNativeErrors.scriptErrorMessage(databaseHandle, execErrorPointer);
  }

  static String errorString(int resultCode) {
    return SqliteNativeErrors.errorString(resultCode);
  }

  static String errorString(
      int resultCode, MethodHandle errorStringHandle, MethodHandle strlenHandle) {
    return SqliteNativeErrors.errorString(resultCode, errorStringHandle, strlenHandle);
  }

  static String scriptErrorMessage(
      int resultCode,
      @Nullable MemorySegment execErrorPointer,
      MethodHandle errorStringHandle,
      MethodHandle strlenHandle) {
    return SqliteNativeErrors.scriptErrorMessage(
        resultCode, execErrorPointer, errorStringHandle, strlenHandle);
  }

  static String scriptErrorMessage(
      MemorySegment databaseHandle,
      @Nullable MemorySegment execErrorPointer,
      MethodHandle errorMessageHandle,
      MethodHandle strlenHandle) {
    return SqliteNativeErrors.scriptErrorMessage(
        databaseHandle, execErrorPointer, errorMessageHandle, strlenHandle);
  }

  static String sqliteVersion() {
    return SqliteNativeBootstrap.sqliteVersion();
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle) {
    return SqliteNativeBootstrap.sqliteVersion(libraryVersionHandle);
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle, MethodHandle strlenHandle) {
    return SqliteNativeBootstrap.sqliteVersion(libraryVersionHandle, strlenHandle);
  }

  static String sqlite3MultipleCiphersVersion() {
    return SqliteNativeBootstrap.sqlite3MultipleCiphersVersion();
  }

  static String sqlite3MultipleCiphersVersion(MethodHandle versionHandle) {
    return SqliteNativeBootstrap.sqlite3MultipleCiphersVersion(versionHandle);
  }

  static String sqlite3MultipleCiphersVersion(
      MethodHandle versionHandle, MethodHandle strlenHandle) {
    return SqliteNativeBootstrap.sqlite3MultipleCiphersVersion(versionHandle, strlenHandle);
  }

  static String configuredLibraryMode() {
    return SqliteRuntime.LIBRARY_MODE;
  }

  static int compareVersions(String leftVersion, String rightVersion) {
    return SqliteNativeRuntimeSupport.compareVersions(leftVersion, rightVersion);
  }

  static String requireSupportedVersion(String loadedVersion, String libraryMode) {
    return SqliteNativeRuntimeSupport.requireSupportedVersion(loadedVersion, libraryMode);
  }

  static String requireSupportedSqlite3mcVersion(String loadedVersion, String libraryMode) {
    return SqliteNativeRuntimeSupport.requireSupportedSqlite3mcVersion(loadedVersion, libraryMode);
  }

  static void requireSupportedCompileOptions(
      MethodHandle compileOptionUsedHandle,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String libraryMode) {
    SqliteNativeRuntimeSupport.requireSupportedCompileOptions(
        compileOptionUsedHandle, loadedSqliteVersion, loadedSqlite3mcVersion, libraryMode);
  }

  static boolean compileOptionUsed(MethodHandle compileOptionUsedHandle, String compileOption) {
    return SqliteNativeRuntimeSupport.compileOptionUsed(compileOptionUsedHandle, compileOption);
  }

  static String resultName(int resultCode) {
    return SqliteNativeErrors.resultName(resultCode);
  }

  static SqliteNativeApi api() {
    return SqliteNativeBootstrap.api();
  }

  static <T> T initialize(Supplier<T> initializer) {
    return SqliteNativeBootstrap.initialize(initializer);
  }

  static IllegalStateException nativeInitializationFailure(ExceptionInInitializerError error) {
    return SqliteNativeBootstrap.nativeInitializationFailure(error);
  }

  static SqliteLibraryTarget configuredLibraryTarget(@Nullable String configuredLibraryPath) {
    return SqliteNativeRuntimeSupport.configuredLibraryTarget(configuredLibraryPath);
  }

  static SqliteLibraryTarget configuredLibraryTarget(
      @Nullable String configuredLibraryPath, @Nullable String bundleHomePath) {
    return SqliteNativeRuntimeSupport.configuredLibraryTarget(
        configuredLibraryPath, bundleHomePath);
  }

  static String supportedNativeLibraryFileName() {
    return SqliteNativeRuntimeSupport.supportedNativeLibraryFileName();
  }

  static void freeSqliteBuffer(MemorySegment pointer, MethodHandle freeHandle) {
    SqliteNativeErrors.freeSqliteBuffer(pointer, freeHandle);
  }

  static int activeConnectionCount() {
    return SqliteNativeBootstrap.activeConnectionCount();
  }

  static void shutdownIfQuiescent(MethodHandle sqlite3Shutdown, int activeConnections) {
    SqliteNativeBootstrap.shutdownIfQuiescent(sqlite3Shutdown, activeConnections);
  }

  static void shutdownQuietly(MethodHandle sqlite3Shutdown) {
    SqliteNativeBootstrap.shutdownQuietly(sqlite3Shutdown);
  }

  static MemorySegment sqliteTransient() {
    return SQLITE_TRANSIENT;
  }
}
