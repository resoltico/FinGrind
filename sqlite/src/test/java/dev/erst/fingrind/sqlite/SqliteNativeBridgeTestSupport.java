package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Shared native API fixtures and handle helpers for split SQLite bridge tests. */
class SqliteNativeBridgeTestSupport {
  static final String TEST_BOOK_KEY = "native-library-test-book-key";
  static final int SQLITE_API_ARGUMENT_OPEN_V2 = 1;
  static final int SQLITE_API_ARGUMENT_CLOSE_V2 = 2;
  static final int SQLITE_API_ARGUMENT_KEY = 3;
  static final int SQLITE_API_ARGUMENT_SHUTDOWN = 5;
  static final int SQLITE_API_ARGUMENT_BUSY_TIMEOUT = 6;
  static final int SQLITE_API_ARGUMENT_EXTENDED_RESULT_CODES = 7;
  static final int SQLITE_API_ARGUMENT_EXEC = 12;
  static final int SQLITE_API_ARGUMENT_ERRMSG = 25;
  static final int SQLITE_API_ARGUMENT_ERRSTR = 26;
  static final int SQLITE_API_ARGUMENT_EXTENDED_ERRCODE = 27;
  static final int SQLITE_API_ARGUMENT_LOADED_VERSION = 28;
  static final int SQLITE_API_ARGUMENT_LOADED_SQLITE3MC_VERSION = 29;
  static final int SQLITE_API_ARGUMENT_LOADED_SOURCE_ID = 30;
  static final int SQLITE_API_ARGUMENT_LOADED_LIBRARY_PATH = 32;

  protected SqliteNativeBridgeTestSupport() {}

  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  static SqliteNativeApi sqliteApi(
      MethodHandle keyHandle,
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    return SqliteNativeApiFixture.sqliteApi(
        keyHandle, closeHandle, errorMessageHandle, errorStringHandle, extendedErrcodeHandle);
  }

  static SqliteNativeApi sqliteApi(
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    return SqliteNativeApiFixture.sqliteApi(
        closeHandle, errorMessageHandle, errorStringHandle, extendedErrcodeHandle);
  }

  static Object[] defaultSqliteApiArguments() {
    return SqliteNativeApiFixture.defaultSqliteApiArguments();
  }

  static SqliteNativeApi buildSqliteApi(Object[] sqliteApiArguments) {
    return SqliteNativeApiFixture.buildSqliteApi(sqliteApiArguments);
  }

  BookAccess bookAccess(Path bookPath) {
    return bookAccess(bookPath, TEST_BOOK_KEY);
  }

  BookAccess bookAccess(Path bookPath, String keyText) {
    try {
      Path keyPath = tempDirectory.resolve("book-keys").resolve(bookPath.getFileName() + ".key");
      writeSecureKeyFile(keyPath, keyText);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  static MethodHandle constantMethodHandle(Object value, Class<?>... parameterTypes) {
    return SqliteNativeHandleFixtures.constantMethodHandle(value, parameterTypes);
  }

  static MethodHandle throwingMethodHandle(
      Throwable throwable, Class<?> returnType, Class<?>... parameterTypes) {
    return SqliteNativeHandleFixtures.throwingMethodHandle(throwable, returnType, parameterTypes);
  }

  static MethodHandle voidMethodHandle(Class<?>... parameterTypes) {
    return SqliteNativeHandleFixtures.voidMethodHandle(parameterTypes);
  }

  static Class<?> constantType(Object value) {
    return SqliteNativeHandleFixtures.constantType(value);
  }

  static String expectedNativeLibraryFileName() {
    return SqliteNativeHandleFixtures.expectedNativeLibraryFileName();
  }

  static void restoreSystemProperty(String key, String value) {
    SqliteNativeHandleFixtures.restoreSystemProperty(key, value);
  }

  static int recordShutdownCall(AtomicInteger shutdownCalls) {
    return SqliteNativeHandleFixtures.recordShutdownCall(shutdownCalls);
  }

  static int recordCloseCall(AtomicInteger closeCalls, MemorySegment databaseHandle) {
    return SqliteNativeHandleFixtures.recordCloseCall(closeCalls, databaseHandle);
  }

  static int recordCloseCallThenThrow(AtomicInteger closeCalls, MemorySegment databaseHandle) {
    return SqliteNativeHandleFixtures.recordCloseCallThenThrow(closeCalls, databaseHandle);
  }

  static int failThenSucceedCloseCall(AtomicInteger closeCalls, MemorySegment databaseHandle) {
    return SqliteNativeHandleFixtures.failThenSucceedCloseCall(closeCalls, databaseHandle);
  }

  static int failThenDelegateCloseCall(
      AtomicInteger closeCalls,
      SqliteNativeCalls.AddressToIntCall delegateClose,
      MemorySegment databaseHandle) {
    return SqliteNativeHandleFixtures.failThenDelegateCloseCall(
        closeCalls, delegateClose, databaseHandle);
  }

  static int throwIllegalStateThenSucceedCloseCall(
      AtomicInteger closeCalls, MemorySegment databaseHandle) {
    return SqliteNativeHandleFixtures.throwIllegalStateThenSucceedCloseCall(
        closeCalls, databaseHandle);
  }

  static int throwIllegalStateThenDelegateCloseCall(
      AtomicInteger closeCalls,
      SqliteNativeCalls.AddressToIntCall delegateClose,
      MemorySegment databaseHandle) {
    return SqliteNativeHandleFixtures.throwIllegalStateThenDelegateCloseCall(
        closeCalls, delegateClose, databaseHandle);
  }

  static int throwAssertionThenSucceedCloseCall(
      AtomicInteger closeCalls, MemorySegment databaseHandle) {
    return SqliteNativeHandleFixtures.throwAssertionThenSucceedCloseCall(
        closeCalls, databaseHandle);
  }

  static int throwAssertionThenDelegateCloseCall(
      AtomicInteger closeCalls,
      SqliteNativeCalls.AddressToIntCall delegateClose,
      MemorySegment databaseHandle) {
    return SqliteNativeHandleFixtures.throwAssertionThenDelegateCloseCall(
        closeCalls, delegateClose, databaseHandle);
  }

  static int openWithDatabaseHandle(
      MemorySegment openedHandle,
      MemorySegment filename,
      MemorySegment databasePointer,
      int flags,
      MemorySegment vfs) {
    return SqliteNativeHandleFixtures.openWithDatabaseHandle(
        openedHandle, filename, databasePointer, flags, vfs);
  }

  static int failOpenWithDatabaseHandle(
      MemorySegment openedHandle,
      MemorySegment filename,
      MemorySegment databasePointer,
      int flags,
      MemorySegment vfs) {
    return SqliteNativeHandleFixtures.failOpenWithDatabaseHandle(
        openedHandle, filename, databasePointer, flags, vfs);
  }

  static void writeSecureKeyFile(Path keyPath, String keyText) throws IOException {
    SqliteNativeDatabaseFixtures.writeSecureKeyFile(keyPath, keyText);
  }

  static void withOpenDatabase(BookAccess bookAccess, SqliteDatabaseAction action) {
    SqliteNativeDatabaseFixtures.withOpenDatabase(bookAccess, action);
  }

  static SqliteNativeDatabase openNativeDatabase(BookAccess bookAccess) {
    return SqliteNativeDatabaseFixtures.openNativeDatabase(bookAccess);
  }

  /** Performs one checked action against a temporary native SQLite handle. */
  @FunctionalInterface
  interface SqliteDatabaseAction {
    void run(SqliteNativeDatabase database);
  }
}
