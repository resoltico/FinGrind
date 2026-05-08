package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeOpenFailureHandlingTest extends SqliteNativeBridgeTestSupport {
  @Test
  void open_wrapsUnexpectedThrowableFromOpenInvocation() throws Exception {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[1] =
        throwingMethodHandle(
            new IllegalStateException("boom"),
            int.class,
            MemorySegment.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class);
    SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
    try (SqliteBookPassphrase passphrase =
        SqliteBookPassphrase.fromCharacters("native open throwable", TEST_BOOK_KEY.toCharArray())) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-throwable.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("Failed to open the SQLite native library bridge.", exception.getMessage());
      assertEquals("boom", Objects.requireNonNull(exception.getCause()).getMessage());
    }
  }

  @Test
  void open_closesNativeHandleWhenKeyValidationFails() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open validation failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "openWithDatabaseHandle",
                      java.lang.invoke.MethodType.methodType(
                          int.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          int.class,
                          MemorySegment.class)),
              0,
              fakeDatabaseHandle);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_EXEC] =
          constantMethodHandle(
              26,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class);
      sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] =
          constantMethodHandle(arena.allocateFrom("file is not a database"), int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-validation-failure.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("SQLITE_NOTADB", exception.resultName());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  void configureOpenedDatabase_rethrowsErrorsAndClosesNativeHandle() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "configure-opened-error", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_BUSY_TIMEOUT] =
          throwingMethodHandle(
              new AssertionError("boom"), int.class, MemorySegment.class, int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      AssertionError exception =
          assertThrows(
              AssertionError.class,
              () ->
                  SqliteNativeConnections.configureOpenedDatabase(
                      fakeDatabaseHandle, passphrase, sqliteApi, arena));
      assertEquals("boom", exception.getMessage());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  void configureOpenedDatabase_addsSuppressedCloseFailureWhenCleanupCloseReturnsNonOk()
      throws Exception {
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "configure-opened-close-failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          constantMethodHandle(14, MemorySegment.class);
      sqliteApiArguments[SQLITE_API_ARGUMENT_BUSY_TIMEOUT] =
          throwingMethodHandle(
              new IllegalStateException("busy-timeout boom"),
              int.class,
              MemorySegment.class,
              int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeConnections.configureOpenedDatabase(
                      fakeDatabaseHandle, passphrase, sqliteApi, arena));
      assertEquals("Failed to open the SQLite native library bridge.", exception.getMessage());
      assertEquals("busy-timeout boom", Objects.requireNonNull(exception.getCause()).getMessage());
      assertEquals(1, exception.getSuppressed().length);
    }
  }

  @Test
  void open_closesNativeHandleWhenConfigurationThrowsUnexpectedly() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open configuration failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "openWithDatabaseHandle",
                      java.lang.invoke.MethodType.methodType(
                          int.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          int.class,
                          MemorySegment.class)),
              0,
              fakeDatabaseHandle);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_BUSY_TIMEOUT] =
          throwingMethodHandle(
              new IllegalStateException("busy-timeout boom"),
              int.class,
              MemorySegment.class,
              int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-configuration-failure.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("Failed to open the SQLite native library bridge.", exception.getMessage());
      assertEquals("busy-timeout boom", Objects.requireNonNull(exception.getCause()).getMessage());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  void open_preservesNativeOpenFailureWhenCleanupCloseThrows() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open cleanup failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "failOpenWithDatabaseHandle",
                      java.lang.invoke.MethodType.methodType(
                          int.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          int.class,
                          MemorySegment.class)),
              0,
              fakeDatabaseHandle);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCallThenThrow",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] =
          constantMethodHandle(arena.allocateFrom("open boom"), int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-native-failure.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("SQLITE_CANTOPEN: open boom", exception.getMessage());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  void open_preservesNativeOpenFailureWhenNoHandleIsReturned() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open null handle failure", TEST_BOOK_KEY.toCharArray())) {
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          constantMethodHandle(
              14, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] =
          constantMethodHandle(arena.allocateFrom("open boom"), int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-no-handle-failure.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("SQLITE_CANTOPEN: open boom", exception.getMessage());
      assertEquals(0, closeCalls.get());
    }
  }

  @Test
  void requireOpenConfigurationSuccess_throwsSqliteFailureForNonOkResult() throws Exception {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));
      SqliteNativeException sqliteException =
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeConnections.requireOpenConfigurationSuccess(14, sqliteApi));
      assertEquals(14, sqliteException.resultCode());
      assertEquals("SQLITE_CANTOPEN", sqliteException.resultName());
      assertEquals("SQLITE_CANTOPEN: boom", sqliteException.getMessage());
    }
  }

  @Test
  void requireOpenConfigurationSuccess_preservesNativeFailureMessage() throws Exception {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeConnections.requireOpenConfigurationSuccess(14, sqliteApi));
      assertEquals("SQLITE_CANTOPEN: boom", exception.getMessage());
    }
  }

  @Test
  void requireOpenConfigurationSuccess_usesResultNameWhenErrorStringIsBlank() throws Exception {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("unused"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom(""), int.class),
              constantMethodHandle(14, MemorySegment.class));
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeConnections.requireOpenConfigurationSuccess(14, sqliteApi));
      assertEquals("SQLITE_CANTOPEN", exception.getMessage());
    }
  }
}
